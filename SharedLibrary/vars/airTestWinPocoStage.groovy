import lib.*
def call(BuildArgs){
    stage("Poco"){node(BuildArgs.AssignedLabel_Win){
        print(NODE_NAME)
        currentBuild.description +="_Windows"// so we know this run is for PC
        try{
            def outputRoot = Utility.OS(this).Path("${env.GitRootPoco}\\${env.OutputFolderRoot}\\windows")
            dir(env.GitRootPoco +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                deleteDir()
            }
            stage('Build Windows') {
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
                overwriteBundleData("BundleDataStandaloneWindows64.asset", env.GitRootPoco +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                    // clean previous build output
                dir(outputRoot){deleteDir()}
                BuildArgs.Poco.each{resourceFile, projectFile->l:{
                    writeFile(text:libraryResource(resourceFile), file:env.GitRootPoco + "\\"+projectFile)
                }}
                unityExec(unityExePath:env.UnityPathWin, projectPath:env.GitRootPoco +"\\"+env.ProjectPathClient, 
                        executeMethod:env.ExecBuildWin, options:env.Options, output:outputRoot, apk:"Poco_"+env.ProductName +"\\Poco_"+env.WinName,
                        logKeyWords:BuildArgs.UnityErrorCheck,
                        last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:"Poco_"+env.WinName,
                        buildAssetBundle:env.ExecBuildABWin)
            }
            stage('Release Windows') {
                compress(zip:"${outputRoot}\\Poco_${env.ProductName}.zip", 
                    path:"${outputRoot}\\Poco_${env.ProductName}", mode:"1",relpath:".")
                if(env.OutputShare != null){
                    robocopy("${outputRoot}", env.OutputShare+"\\"+env.OutputShareClient+"\\Windows" ," Poco_${env.ProductName}.zip ")
                    currentBuild.description +="<br/>" + env.OutputShare+"_Poco\\"+env.OutputShareClient
                }
                if(BuildArgs.ClientUploadFTP){
                        BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                            withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                def zipFilePath = Utility.OS(this).Path("${outputRoot}/Poco_${env.ProductName}.zip")
                                ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}_Windows/Poco", path:zipFilePath)
                            }
                            currentBuild.description +="<br/>ftp://" + ip + remote + "/${env.PackageName}_Windows/Poco"
                        }}
                    }
            }
            
            stage("Release Windows AB"){
                if(env.UploadAB != null){
                    robocopy(env.GitRootPoco +"\\"+env.ProjectPathClient+"\\AssetBundles\\StandaloneWindows64",env.UploadAB+"\\${env.UploadABFolder}\\StandaloneWindows64"," /E /S ")
                }
            }
        }
        finally{
            Utility.CleanParallelJobWorkSpace(this)
        }
    }}
}