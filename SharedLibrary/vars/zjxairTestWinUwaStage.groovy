import lib.*
def call(BuildArgs){
    stage("Uwagot"){
        node(BuildArgs.AssignedLabel_Android){
            print(NODE_NAME)
            currentBuild.description +="_Windows"
            try{
                overwriteBundleData("BundleDataAndroid.asset", env.GitRootUwa +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                cleanOutput(env.GitRootUwa +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                def outputRoot = "${env.GitRootUwa}\\${env.OutputFolderRoot}\\Uwa"
                stage('Build windows') {
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
                    //手动或自动
                    
                    BuildArgs."${params.Profiler}".each{resourceFile, projectFile->l:{
                        writeFile(text:libraryResource(resourceFile), file:env.GitRootUwa + "\\"+projectFile)
                    }}
                    unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootUwa +"\\"+env.ProjectPathClient, 
                        executeMethod:env.ExecBuildWin, options:env.Options, output:"${outputRoot}\\${env.ProductName}", apk:"\\Uwagot_"+env.WinName,
                        logKeyWords:BuildArgs.UnityErrorCheck,
                        last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:"Uwagot_"+env.WinName,
                        buildAssetBundle:env.ExecBuildABWin)
                }
                stage("Release Uwagot") {
                     compress(zip:"${outputRoot}\\Uwagot_${env.ProductName}.zip", 
                    path:"${outputRoot}\\${env.ProductName}\\Uwagot_${env.ProductName}", mode:"1",relpath:".")
                    if(env.OutputShare != null){
                        robocopy("${outputRoot}",env.OutputShare+"_Uwagot\\"+env.OutputShareClient+"\\Windows","*.zip *.exe" )
                        currentBuild.description +="<br/>" + env.OutputShare+"_Uwagot\\"+env.OutputShareClient
                    }
                    if(BuildArgs.ClientUploadFTP){
                        BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                            withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                def zipFilePath = Utility.OS(this).Path("${outputRoot}/${env.ProductName}.zip")
                                ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}_Windows/Uwagot", path:zipFilePath)
                                if(env.ResaveFolder != null){
                                    def resavePath = Utility.OS(this).Path(env.GitRootUwa +"\\"+env.ResaveFolder)
                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/Uwagot", path:resavePath)
                                }
                                def eABPath = Utility.OS(this).Path( "${env.GitRootUwa}\\${env.ProjectPathClient}\\ExportAssetBundle")
                                if(psTestPath(eABPath)){
                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/Uwagot", path:eABPath)
                                }
                            }
                            currentBuild.description +="<br/>" +"ftp://" + ip + remote + "/${env.PackageName}_Windows/Uwagot"
                        }}
                    }
                    
                }
                if(env.UploadAB != null){
                    stage("Release Uwagot AB"){
                        robocopy(env.GitRootUwa +"\\"+env.ProjectPathClient+"\\AssetBundles",env.UploadAB+"\\${env.UploadABFolder}"," /E /S ")
                    }
                }
            }
            finally{
                Utility.CleanParallelJobWorkSpace(this)
            }
        }
}
}