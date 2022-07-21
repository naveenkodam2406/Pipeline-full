import lib.*
def call(BuildArgs){
    stage("Poco"){
        node(BuildArgs.AssignedLabel_Android){
            print(NODE_NAME)
            try{
                overwriteBundleData("BundleDataAndroid.asset", env.GitRootPoco +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                cleanOutput(env.GitRootPoco +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                def outputRoot = "${env.GitRootPoco}\\${env.OutputFolderRoot}\\Poco"
                stage('Build Android') {
                    cleanOutput(outputRoot)
                    if(BuildArgs.GameClientSetting){
                        BuildArgs.GameClientSetting.each{k,v ->l:{
                            if( env.UploadABFolder != null){
                                if(env.BuildNumberForDLC=="true"){
                                    env.UploadABFolder = env.BUILD_NUMBER
                                }
                                env.UploadABFolder += "_Poco"
                            }
                            def clientconf = readFile(env.GitRootPoco + "\\" +k)
                            v.each{reg, val->j:{
                                clientconf = clientconf.replace(Utility.RegExFind(clientconf,reg), val)
                                if(env.UploadABFolder != null){
                                    clientconf = clientconf.replace("{params.versionPrefix}","${env.UploadABFolder}")
                                }
                                writeFile(file: env.GitRootPoco + "\\" +k, text: clientconf , encoding: "UTF-8")
                            }}
                        }}
                    }
                    if(BuildArgs.GameConfig && params.GameConfig != null){
                        BuildArgs.GameConfig."${params.GameConfig}".each{source, dest ->l:{
                            writeFile(text:libraryResource(source), file:env.GitRootPoco +"\\"+env.ProjectPathClient + "\\"+dest)
                            print readFile(env.GitRootPoco +"\\"+env.ProjectPathClient + "\\"+dest)
                        }}
                    }
                    dir(env.GitRootPoco +"\\"+env.ProjectPathClient+"\\Assets\\Plugins\\BJFramework\\JsonDotNet"){
                        deleteDir()
                    }
                    BuildArgs.Poco.each{resourceFile, projectFile->l:{
                        writeFile(text:libraryResource(resourceFile), file:env.GitRootPoco + "\\"+projectFile)
                    }}
                    unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootPoco +"\\"+env.ProjectPathClient, 
                        executeMethod:env.ExecBuildAndroid, options:env.Options, output:"${outputRoot}\\${env.ProductName}", apk:"Poco_"+env.ApkName,
                        logKeyWords:BuildArgs.UnityErrorCheck,
                        last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:"Poco_"+env.ApkName,
                        buildAssetBundle:env.ExecBuildABAndroid)
                }
                stage("Release Android") {
                    if(env.OutputShare != null){
                        releaseResave(GitRoot:env.GitRootPoco, OutputRoot:"${outputRoot}\\${env.ProductName}")
                        releaseEAB(GitRoot:env.GitRootPoco, OutputRoot:"${outputRoot}\\${env.ProductName}")
                        robocopy("${outputRoot}\\${env.ProductName}", env.OutputShare+"_Poco\\"+env.OutputShareClient+"\\Android"," *.zip *.apk ")
                        currentBuild.description +="<br/> " + env.OutputShare+"_Poco\\"+env.OutputShareClient
                    }
                    if(BuildArgs.ClientUploadFTP){
                        BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                            withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                def apkPath = Utility.OS(this).Path("${outputRoot}/${env.ProductName}/Poco_${env.ApkName}")
                                ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}_Android/Poco", path:apkPath)
                                def resavePath = Utility.OS(this).Path("${env.GitRootPoco}\\${env.ProjectPathClient}\\Resave")
                                if(testPath(resavePath)){
                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}_Android/Poco", path:resavePath)
                                }
                                def eABPath = Utility.OS(this).Path( "${env.GitRootPoco}\\${env.ProjectPathClient}\\ExportAssetBundle")
                                if(testPath(eABPath)){
                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}_Android/Poco", path:eABPath)
                                }
                            }
                            currentBuild.description +="<br/>ftp://" + ip + remote + "/${env.PackageName}_Android/Poco"
                        }}
                    }
                    if(BuildArgs.AutoTestFTP){
                        dir(outputRoot){
                            try{
                                BuildArgs.AutoTestFTP.each{ip, tag->l:{
                                    def credDesc = ip
                                    if(BuildArgs.AutoTestFTPCred != null) credDesc = BuildArgs.AutoTestFTPCred
                                    withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(credDesc), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                        def apkPath = Utility.OS(this).Path("${outputRoot}/${env.ProductName}/Poco_${env.ApkName}")
                                        bat("ren ${apkPath} ${tag}.apk")
                                        apkPath = Utility.OS(this).Path("${outputRoot}/${env.ProductName}/${tag}.apk")
                                        ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:"", path:apkPath)
                                        def filelistContent = ""
                                        filelistContent = "${tag}.apk\r\n"
                                        def resavePath = Utility.OS(this).Path("${env.GitRootPoco}\\${env.ProjectPathClient}\\Resave")
                                        
                                        if(testPath(resavePath)){
                                            compress(zip:"${outputRoot}\\${tag}.apk.Resave.zip",path:"${resavePath}",relpath:"..")
                                            ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:"", path:"${outputRoot}/${tag}.apk.Resave.zip")
                                            filelistContent += "${tag}.apk.Resave.zip\r\n"
                                        }
                                        def eABPath = Utility.OS(this).Path( "${env.GitRootPoco}\\${env.ProjectPathClient}\\ExportAssetBundle")
                                        if(testPath(eABPath)){
                                            compress(zip:"${outputRoot}\\${tag}.apk.ExportAssetBundle.zip",path:"${eABPath}",relpath:"..")
                                            ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:"", path:"${outputRoot}/${tag}.apk.ExportAssetBundle.zip")
                                            filelistContent += "${tag}.apk.ExportAssetBundle.zip\r\n"
                                        }
                                        try{
                                            writeFile(file: "${outputRoot}\\${tag}.apk.filelist", text: filelistContent , encoding: "UTF-8")
                                            ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:"", path:"${outputRoot}/${tag}.apk.filelist")
                                        }
                                        catch(Exception ex){
                                            print ex
                                        }
                                    }
                                }}
                                if(BuildArgs.AutoTestUrl && env.TestScheme && !env.TestScheme.startsWith("---")){
                                    env.TestScheme.tokenize(",").each{scheme_id -> l:{
                                        def url = BuildArgs.AutoTestUrl.replace("{scheme_id}",scheme_id)
                                        elasticQuery("addToAutoTest", """--url ${url} --data "{'name':'${env.JOB_NAME}.${env.BUILD_NUMBER}','creator':'JENKINS','extraInfo':'${env.BUILD_URL}'}" """)
                                        currentBuild.description +="<br/>" + env.AutoTestInfo
                                    }}
                                }
                            }
                            catch(Exception ex){
                                print ("PocpAutoTestFTP Failed with Exception")
                                print ex
                            }
                        }
                        
                    }
                }
                if(env.UploadAB != null){
                    stage("Release Android AB"){
                        robocopy(env.GitRootPoco +"\\"+env.ProjectPathClient+"\\AssetBundles\\Android",env.UploadAB+"\\${env.UploadABFolder}\\Android"," /E /S ")
                    }
                }
            }
            finally{
                if(testPath(env.GitRootPoco +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")){
                    archiveFolder(env.GitRootPoco +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                }
                Utility.CleanParallelJobWorkSpace(this)
            }
        }
}
}