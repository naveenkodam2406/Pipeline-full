import lib.*
def call(BuildArgs){
    stage("Uwagot"){
        node(BuildArgs.AssignedLabel_Android){
            print(NODE_NAME)
            try{
                overwriteBundleData("BundleDataAndroid.asset", env.GitRootUwa +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                cleanOutput(env.GitRootUwa +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                def outputRoot = "${env.GitRootUwa}\\${env.OutputFolderRoot}\\Uwa"
                stage('Build Uwagot') {
                    cleanOutput(outputRoot)
                    if(BuildArgs.GameClientSetting){
                        BuildArgs.GameClientSetting.each{k,v ->l:{
                            if( env.UploadABFolder != null){
                                if(env.BuildNumberForDLC=="true"){
                                    env.UploadABFolder = env.BUILD_NUMBER
                                }
                                env.UploadABFolder += "_Uwagot"
                            }
                            def clientconf = readFile(env.GitRootUwa + "\\" +k)
                            v.each{reg, val->j:{
                                clientconf = clientconf.replace(Utility.RegExFind(clientconf,reg), val)
                                if(env.UploadABFolder != null){
                                    clientconf = clientconf.replace("{params.versionPrefix}","${env.UploadABFolder}")
                                }
                                writeFile(file: env.GitRootUwa + "\\" +k, text: clientconf , encoding: "UTF-8")
                            }}
                        }}
                    }
                    if(BuildArgs.GameConfig && params.GameConfig != null){
                        BuildArgs.GameConfig."${params.GameConfig}".each{source, dest ->l:{
                            writeFile(text:libraryResource(source), file:env.GitRootUwa +"\\"+env.ProjectPathClient + "\\"+dest)
                            print readFile(env.GitRootUwa +"\\"+env.ProjectPathClient + "\\"+dest)
                        }}
                    }
                    dir(env.GitRootUwa +"\\"+env.ProjectPathClient+"\\Assets\\Plugins\\BJFramework\\JsonDotNet"){
                        deleteDir()
                    }
                    BuildArgs."${params.Profiler}".each{resourceFile, projectFile->l:{
                        writeFile(text:libraryResource(resourceFile), file:env.GitRootUwa + "\\"+projectFile)
                    }}
                    unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootUwa +"\\"+env.ProjectPathClient, 
                        executeMethod:env.ExecBuildAndroid, options:env.Options, output:"${outputRoot}\\${env.ProductName}", apk:"Uwagot_"+env.ApkName,
                        logKeyWords:BuildArgs.UnityErrorCheck,
                        last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:"Uwagot_"+env.ApkName,
                        buildAssetBundle:env.ExecBuildABAndroid)
                }
                stage("Release Uwagot") {
                    if(env.OutputShare != null){
                        releaseResave(GitRoot:env.GitRootUwa, OutputRoot:"${outputRoot}\\${env.ProductName}")
                        releaseEAB(GitRoot:env.GitRootUwa, OutputRoot:"${outputRoot}\\${env.ProductName}")
                        robocopy("${outputRoot}\\${env.ProductName}",env.OutputShare+"_Uwagot\\"+env.OutputShareClient+"\\Android"," *.zip *.apk ")
                        currentBuild.description +="<br/>" + env.OutputShare+"_Uwagot\\"+env.OutputShareClient
                    }
                    if(BuildArgs.ClientUploadFTP){
                        BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                             def ftpCred = ip
                            if(BuildArgs.ClientUploadFTPCred != null) ftpCred = BuildArgs.ClientUploadFTPCred
                            withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                def apkPath = Utility.OS(this).Path("${outputRoot}/${env.ProductName}/Uwagot_${env.ApkName}")
                                ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/Uwagot", path:apkPath)
                                def resavePath = Utility.OS(this).Path(env.GitRootUwa +"\\"+env.ResaveFolder)
                                if(testPath(resavePath)){               
                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/Uwagot", path:resavePath)
                                }
                                def eABPath = Utility.OS(this).Path( "${env.GitRootUwa}\\${env.ProjectPathClient}\\ExportAssetBundle")
                                if(testPath(eABPath)){

                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/Uwagot", path:eABPath)
                                }
                            }
                            currentBuild.description +="<br/>" +"ftp://" + ip + remote + "/${env.PackageName}/Uwagot"
                        }}
                    }
                    if(BuildArgs.UwaAutoTestFTP){
                        dir(outputRoot){
                            try{
                                BuildArgs.UwaAutoTestFTP.each{ip, tag->l:{
                                    def credDesc = ip
                                    if(BuildArgs.AutoTestFTPCred != null) credDesc = BuildArgs.AutoTestFTPCred
                                    withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(credDesc), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                        def apkPath = Utility.OS(this).Path("${outputRoot}/${env.ProductName}/Uwagot_${env.ApkName}")
                                        bat("ren ${apkPath} ${tag}.apk")
                                        apkPath = Utility.OS(this).Path("${outputRoot}/${env.ProductName}/${tag}.apk")
                                        ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:"", path:apkPath)
                                        def resavePath = Utility.OS(this).Path(env.GitRootUwa +"\\"+env.ResaveFolder)
                                        if(testPath(resavePath)){
                                            compress(zip:"${outputRoot}\\${tag}.apk.Resave.zip",path:"${resavePath}",relpath:"..")
                                            ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:"", path:"${outputRoot}/${tag}.apk.Resave.zip")
                                        }
                                        def eABPath = Utility.OS(this).Path( "${env.GitRootPoco}\\${env.ProjectPathClient}\\ExportAssetBundle")
                                        if(testPath(eABPath)){
                                            compress(zip:"${outputRoot}\\${tag}.apk.ExportAssetBundle.zip",path:"${eABPath}",relpath:"..")
                                            ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:"", path:"${outputRoot}/${tag}.apk.ExportAssetBundle.zip")
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
                                print ("UwaAutoTestFTP Failed with Exception")
                                print ex
                            }
                        }
                        
                    }
                }
                if(env.UploadAB != null){
                    stage("Release Uwagot AB"){
                        robocopy(env.GitRootUwa +"\\"+env.ProjectPathClient+"\\AssetBundles",env.UploadAB+"\\${env.UploadABFolder}"," /E /S ")
                    }
                }
            }
            finally{
                if(testPath(env.GitRootUwa +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")){
                    archiveFolder(env.GitRootUwa +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                }
                Utility.CleanParallelJobWorkSpace(this)
            }
        }
}
}