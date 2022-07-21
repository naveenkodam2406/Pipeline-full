import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    def scmRS = []
    def scmList = []
    def clientBuildMD5 = ""
    def clientAbMD5 = ""
    def manifestVersion = [:]
    def nodeList = []
    pipeline{
        agent none
        options {
            timestamps()
            timeout(time: 5, unit: 'HOURS')
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "20"))
        }
        stages{
            stage("EnvSetup"){
                steps{
                    script{
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        env.Options = env.Options.replace("#false","#0").replace("#true","#1")
                        env.SCMStr = ""
                        node(BuildArgs.AssignedLabel){
                            Utility.UpdateJobStatusOnELK(this)
                            //DingSender.SendDingMSG(this,env.DingRobot,"STARTED")
                        }
                        manifestVersion["ClientVersion"] = env.versionCode
                        currentBuild.description = "${env.BUILD_NUMBER}_${env.version}_${env.versionPrefix}"
                        env.Options.split(" ").each{
                            if(it.contains("versionCode")){
                                def tmp = it.split("#")[1].trim()
                                currentBuild.description += "_${tmp}"
                                manifestVersion["ClientVersion"] = tmp
                            }
                            else if(it.contains("bundleVersionStep")){
                                currentBuild.description += "_${it}"
                            }
                        }
                        def suffix = ""
                        if(params.JustBuildAB == true){
                            suffix +="_AB"
                            if(params.BuildServer == true){
                                suffix +="_Server"
                            }
                        }
                        else if(params.BuildClient == false && params.BuildServer == true ){
                            suffix +="_Server"
                        }
                        env.OutputShare = env.OutputShare.replace("_{PLACEHOLDER}",suffix)
                        //env.OutputABHotfixTarget = env.OutputABHotfixTarget.replace("_{PLACEHOLDER}",suffix)
                        currentBuild.description += "<br/>" + env.OutputShare
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull != false }}
                steps{
                    script{
                        parallel(
                            "Git Pull Windows":{
                                stage('Git Pull Windows') {
                                    if(params.Windows == true){
                                        node(BuildArgs.AssignedLabel_Win){
                                            ws(env.GitRootWin){}
                                            dir(env.GitRootWin){
                                                def buildargs = [:] << BuildArgs
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                dir(env.LinkScriptWS){
                                                    bat("""${env.LinkScript} """)
                                                }
                                            }
                                        }
                                    }
                                    else
                                    {
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            },
                            'Git Pull Android&Server':{
                                stage('Git Pull Android&Server') {
                                    if(params.Android == true){
                                        node(BuildArgs.AssignedLabel_Android){
                                            ws(env.GitRootAndroid){}
                                            dir(env.GitRootAndroid){
                                                def buildargs = [:] << BuildArgs
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                dir(env.LinkScriptWS){
                                                    bat("""${env.LinkScript} """)
                                                }
                                            }
                                            gitLogM1([env.GitRootAndroid],"Android",BuildArgs.Submodule)
                                        }
                                    }
                                    else
                                    {
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            },
                            'Git Pull IOS':{
                                stage('Git Pull IOS') {
                                    if( params.IOS == true ){
                                        node(BuildArgs.AssignedLabel_IOS){
                                            ws(env.GitRootIOS){}
                                            dir(env.GitRootIOS){
                                                def buildargs = [:] << BuildArgs
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                dir(env.LinkScriptWS){
                                                    sh("chmod 777 ./${env.LinkScriptIOS}")
                                                    sh("./${env.LinkScriptIOS}")
                                                }
                                            }
                                        }
                                    }
                                    else{
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            }
                        )
                        if(scmRS.size() > 0){
                            print scmRS
                            Utility.SetDisplayName(scmRS, currentBuild, scmList,true)
                            def elkscmdata =[:]
                            elkscmdata["SCM"]= scmList
                            env.SCMStr = Utility.ObjToJsonStr(elkscmdata).replace("\"","'")
                            env.SCMStr = env.SCMStr.substring(1,env.SCMStr.length()-1 )+","
                            changeListsFileV2(scmRS,BuildArgs.AssignedLabel)
                        }
                    }
                }
            }
            stage("Build"){
                steps{
                    script{
                        parallel(
                            "IOS":{
                                stage("IOS"){
                                    if(params.IOS == true  && params.BuildClient == true ){
                                        node(BuildArgs.AssignedLabel_IOS){
                                            print(NODE_NAME)
                                            def mountedFolder = mount(path:env.OutputShare, cred:env.MountCred)
                                            try{
                                                overwriteBundleData("BundleDataiOS.asset", env.GitRootIOS +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                cleanOutput(env.GitRootIOS +"/"+env.ProjectPathClient+"/ResourceCheckResult")
                                                def outputRoot = "${env.GitRootIOS}/${env.OutputFolderRoot}"
                                                def xcodeProjPath = "${outputRoot}/${env.IOSProjectName}"
                                                def xcodeArchivePath = "${outputRoot}/${env.IOSProjectName}_archive"
                                                stage('Build IOS') {
                                                    if(params.BuildClient == true || params.JustBuildAB == true){
                                                        cleanOutput(outputRoot)
                                                        gameClientSettings(BuildArgs, env.GitRootIOS)
                                                        unityExec(unityExePath:env.UnityPathIOS, projectPath:env.GitRootIOS +"/"+env.ProjectPathClient, 
                                                            executeMethod:env.ExecBuildIOS, options:env.Options, output:outputRoot, apk:env.IOSProjectName,
                                                            logKeyWords:BuildArgs.UnityErrorCheck,
                                                            last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,
                                                            buildAssetBundle:env.ExecBuildABIOS)
                                                        withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(env.MacBuildAccount), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                            sh("""security unlock-keychain -p ${pwd}""")
                                                        }
                                                        sh("mkdir -p ${outputRoot}/IOS")
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Export iOS"){
                                                    if( params.JustBuildAB != true){
                                                        parallel(
                                                            "Compress":{
                                                                stage("Compress"){
                                                                    //compress(zip:"${outputRoot}/IOS/Resave.zip" , path:env.GitRootIOS +"/"+env.ResaveFolder, python:"/usr/local/bin/python")
                                                                    if(params.UploadXcodeProject){
                                                                        compress(zip:"${mountedFolder[0]}/xcode_${env.PackageName}.zip",path:"${xcodeProjPath}", mode:"1" , python:"/usr/local/bin/python")
                                                                    }
                                                                }
                                                            },
                                                            "Dev":{
                                                                stage("Dev"){
                                                                    iosExport("xcodeProjPath": xcodeProjPath, "xcodeArchivePath": xcodeArchivePath, plist:"ExportOptions.plist",
                                                                        plistPath: "gameClientSettings\\projectFM\\${env.Region}", "outputRoot": outputRoot, discipline: "Dev",
                                                                        bundleId: "com.blackjack-inc.frontmission", teamId: "T9MRWJ68JK",
                                                                        codeName: "projectf"
                                                                    )
                                                                }
                                                            }
                                                        )
                                                        sh("mkdir -p ${mountedFolder[0]}/${env.OutputShareClient}")
                                                        sh("""rsync -r ${outputRoot}/IOS ${mountedFolder[0]}/${env.OutputShareClient}""")
                                                        mdFive(path:outputRoot+"/IOS", relpath:"..", python:"/usr/local/bin/python")
                                                        sh("find ${mountedFolder[0]} -name '.DS_Store' -type f -delete")
                                                        clientBuildMD5 += readFile("${outputRoot}/IOS/VerifyMd5.txt")
                                                        def ipaName = env.IOSName.replace("{Discipline}", "Dev")
                                                        if(BuildArgs.ClientUploadFTP){
                                                            BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                                                withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/ios/${env.PackageName}", path:"${outputRoot}/IOS/${ipaName}")
                                                                }
                                                            }}
                                                        }
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release IOS AB"){
                                                    def tmp = releaseAB(GitRoot:env.GitRootIOS, OutputRoot: outputRoot,MountedFolder:mountedFolder[0])
                                                    clientAbMD5 += "${tmp}"
                                                    manifestVersion["iOSBundleVersion"] = readFile(env.GitRootIOS +"/"+env.ProjectPathClient+"/AssetBundles/iOS/BundleDataVersion.txt").tokenize(",")[0]
                                                }
                                            }
                                            finally{
                                                archiveFolder(env.GitRootIOS +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                                                unmount(mountedFolder)
                                                Utility.CleanParallelJobWorkSpace(this)
                                            }
                                        }
                                    }
                                    else
                                    {
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            },
                            "Server":{
                                stage("Server"){
                                    if(params.BuildServer == true){
                                        node(BuildArgs.AssignedLabel_Server){
                                            print(NODE_NAME)
                                            try{
                                                def outputRoot = "${env.GitRootWin}\\${env.OutputFolderRoot}\\server"
                                                cleanOutput(outputRoot)
                                                stage('Build Server') {
                                                    // clean previous build output
                                                    cleanOutput(env.GitRootServer+"\\ServerBin")
                                                    dir("${env.GitRootServer}"){
                                                        //msbuild("""${env.ServerSln} ${env.ServerBuildOpt}""", env.MSBuild)
                                                        devenv(solution:env.ServerSln, opt: env.ServerBuildOpt)
                                                    }
                                                }
                                                stage('Release Server') {
                                                    dir("${env.GitRootServer}"){
                                                        bat("PublishBinServer_Debug.bat")
                                                    }
                                                    def ver = Utility.ObjToJsonStr([ServerVersion:env.BuildId],true)
                                                    writeFile(file: env.GitRootServer+"\\ServerBin\\version.txt", text:ver)
                                                    mdFive(path:env.GitRootServer+"\\ServerBin")
                                                    robocopy(env.GitRootServer+"\\ServerBin","${outputRoot}\\Server","/MIR")
                                                    robocopy("${outputRoot}\\Server",env.OutputShare+"\\"+env.OutputShareServer,"/MIR")
                                                    //packServer(env.GitRootServer+"\\ServerBin", null, BuildArgs.ServerConf)
                                                    print "Release Server"
                                                }
                                                stage('Upload Server') {
                                                    try{
                                                        if(params.UploadServer == true ){
                                                            def zipName = "${outputRoot}\\Server.zip"
                                                            compress(zip:zipName, path:"${outputRoot}\\Server", mode:"1",relpath:"..")
                                                            print ("Upload Server ${outputRoot}\\Server.zip")
                                                            if(BuildArgs.ServerUploadFTP){
                                                                uploadServer(path:outputRoot, file: "Server.zip", serverConfPath:BuildArgs.ServerUploadFTP, serverTag:env.UpdateServerTag)
                                                            }
                                                        }
                                                        else{
                                                            Utility.MarkStageAsSkipped(STAGE_NAME)
                                                        }
                                                    }
                                                    catch(Exception ex){
                                                        // set unstable not fail the build
                                                        unstable(message: "Upload to FTP is unstable")
                                                        print ex
                                                    }
                                                }
                                            }
                                            finally{
                                                Utility.CleanParallelJobWorkSpace(this)
                                            }
                                        }
                                    }
                                    else{
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            }, 
                            "Windows":{
                                stage("Windows"){
                                    if(params.Windows == true  && params.BuildClient == true ) {
                                        node(BuildArgs.AssignedLabel_Win){
                                            print(NODE_NAME)
                                            try{
                                                def outputRoot = "${env.GitRootWin}\\${env.OutputFolderRoot}\\windows"
                                                cleanOutput(env.GitRootWin +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                                                stage('Build Windows') {
                                                    if(params.BuildClient == true || params.JustBuildAB == true){
                                                        overwriteBundleData("BundleDataStandaloneWindows64.asset", env.GitRootWin +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                        // clean previous build output
                                                        cleanOutput(outputRoot)
                                                        gameClientSettings(BuildArgs, env.GitRootWin)
                                                        unityExec(unityExePath:env.UnityPathWin, projectPath:env.GitRootWin +"\\"+env.ProjectPathClient, 
                                                            executeMethod:env.ExecBuildWin, options:env.Options,output:"${outputRoot}", apk:env.ProductName +"\\"+env.WinName,
                                                            logKeyWords:BuildArgs.UnityErrorCheck,
                                                            last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:env.WinName,
                                                            buildAssetBundle:env.ExecBuildABWin)
                                                        gitStatus([env.GitRootWin],"Windows", true)
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage('Release Windows') {
                                                    if( params.JustBuildAB != true){
                                                        compress(zip:"${outputRoot}\\Windows\\${env.PackageName}.zip", 
                                                        path:"${outputRoot}\\${env.ProductName}", mode:"1",relpath:"..")
                                                        mdFive(path:outputRoot+"\\Windows", relpath:"..")
                                                        clientBuildMD5 += readFile("${outputRoot}\\Windows\\VerifyMd5.txt")
                                                        robocopy("${outputRoot}\\Windows", env.OutputShare+"\\"+env.OutputShareClient+"\\Windows" ," ${env.PackageName}.zip ")
                                                        if(BuildArgs.ClientUploadFTP){
                                                            BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                                                withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/windows", path:"${outputRoot}\\Windows\\${env.PackageName}.zip")
                                                                }
                                                            }}
                                                        }
                                                        manifestVersion["WindowsBundleVersion"] = readFile(env.GitRootWin +"\\"+env.ProjectPathClient+"\\AssetBundles\\StandaloneWindows64\\BundleDataVersion.txt").tokenize(",")[0]
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release Windows AB"){
                                                    def tmp = releaseAB(GitRoot:env.GitRootWin, OutputRoot: outputRoot)
                                                    clientAbMD5 += "${tmp}"
                                                    manifestVersion["WindowsBundleVersion"] = readFile(env.GitRootWin +"\\"+env.ProjectPathClient+"\\AssetBundles\\StandaloneWindows64\\BundleDataVersion.txt").tokenize(",")[0]
                                                    if(BuildArgs.AbServerUploadFTP && env.Options.contains("AbDownload#1")){
                                                        retry(2){
                                                            try{
                                                                timeout(30){
                                                                    uploadServer(serverConfPath:BuildArgs.AbServerUploadFTP, serverTag:params.AssetBundleDownloadUrlRoot, subLocalFolder: "StandaloneWindows64",
                                                                    subRemote:env.UploadABFolder, path:env.GitRootWin +"\\"+env.ProjectPathClient+"\\AssetBundles")
                                                                }
                                                            }
                                                            catch(Exception e){
                                                                error 'FTP Timeout!'
                                                            }
                                                        }
                                                    }
                                                
                                                }
                                            }
                                            finally{
                                                archiveFolder(env.GitRootWin +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                                                Utility.CleanParallelJobWorkSpace(this)
                                            }
                                        }
                                    }
                                    else{
                                       Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            },
                            'Android':{
                                stage("Android"){
                                    if(params.Android == true && params.BuildClient == true ){
                                        node(BuildArgs.AssignedLabel_Android){
                                            print(NODE_NAME)
                                            try{
                                                overwriteBundleData("BundleDataAndroid.asset", env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                cleanOutput(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                                                def outputRoot = "${env.GitRootAndroid}\\${env.OutputFolderRoot}\\android"
                                                if(env.Options.toLowerCase().contains("version#development")){
                                                    env.UNITY_IL2CPP_ANDROID_USE_LLD_LINKER="1"
                                                    symbolink(env.LLVM21, env.LLVM)
                                                }
                                                else{
                                                    symbolink(env.LLVM19, env.LLVM)
                                                }
                                                stage('Build Android') {
                                                    cleanOutput(outputRoot)
                                                    gameClientSettings(BuildArgs, env.GitRootAndroid)
                                                    //def tmpOpt = env.Options.replace("AbDownload#0","AbDownload#1").replace("useAB#0","useAB#1")
                                                    bat("echo UNITY_IL2CPP_ANDROID_USE_LLD_LINKER %UNITY_IL2CPP_ANDROID_USE_LLD_LINKER%")
                                                    unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootAndroid +"\\"+env.ProjectPathClient, 
                                                        executeMethod:env.ExecBuildAndroid, options:env.Options, output:"${outputRoot}", apk:env.ProductName +"\\"+env.ApkName,
                                                        logKeyWords:BuildArgs.UnityErrorCheck,
                                                        last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:env.ApkName,
                                                        buildAssetBundle:env.ExecBuildABAndroid)
                                                        gitStatus([env.GitRootAndroid],"Android", true)
                                                }
                                                stage("Release Android") {
                                                    if( params.JustBuildAB != true){
                                                        //compress(zip:"${outputRoot}\\${env.ProductName}\\Resave.zip" , path:env.GitRootAndroid +"\\"+env.ResaveFolder)
                                                        
                                                        robocopy(outputRoot+"\\${env.ProductName}",outputRoot+"\\Android"," *.apk ")
                                                        mdFive(path:outputRoot+"\\Android",relpath:"..")
                                                        clientBuildMD5 += readFile("${outputRoot}\\Android\\VerifyMd5.txt")
                                                        robocopy(outputRoot +"\\Android",env.OutputShare+"\\"+env.OutputShareClient+"\\Android"," *.apk")
                                                        
                                                        
                                                        if(BuildArgs.ClientUploadFTP){
                                                            BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                                                withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/android/${env.PackageName}", path:"${outputRoot}/${env.ProductName}/${env.ApkName}")
                                                                    //ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/android/${env.PackageName}", path:env.GitRootAndroid +"\\"+env.ResaveFolder)
                                                                }
                                                            }}
                                                        }
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release Android AB"){
                                                    def tmp = releaseAB(GitRoot:env.GitRootAndroid, OutputRoot: outputRoot)
                                                    clientAbMD5 += "${tmp}"
                                                    manifestVersion["AndroidBundleVersion"] = readFile(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\AssetBundles\\Android\\BundleDataVersion.txt").tokenize(",")[0]
                                                    if(BuildArgs.AbServerUploadFTP && env.Options.contains("AbDownload#1")){
                                                        retry(2){
                                                            try{
                                                                timeout(30){
                                                                    uploadServer(serverConfPath:BuildArgs.AbServerUploadFTP, serverTag:params.AssetBundleDownloadUrlRoot, subLocalFolder: "Android",
                                                                    subRemote:env.UploadABFolder, path:env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\AssetBundles")
                                                                }
                                                            }
                                                            catch(Exception e){
                                                                error 'FTP Timeout!'
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            finally{
                                                archiveFolder(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
                                                Utility.CleanParallelJobWorkSpace(this)
                                            }
                                        }
                                    }
                                    else
                                    {
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            },
                            failFast:env.FailFast == "true"?true:false
                        )
                    }
                }
            }
            stage("Release"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            if(clientBuildMD5 != ""){
                                writeFile(file:env.OutputShare +"\\"+ env.OutputShareClient+"\\VerifyMd5.txt", text: clientBuildMD5)
                            }
                            compress(zip:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}.zip" , path:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}", relpath:".")
                            dir(env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}"){
                                deleteDir()
                            }
                            mdFive(file:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}.zip")
                            clientAbMD5 += env.md5 + " .\\manifest_${env.BUILD_NUMBER}.zip"
                            writeFile(file:env.OutputShare+"\\"+env.OutputShareAB+"\\VerifyMd5.txt", text: clientAbMD5, encoding:"UTF-8")
                            writeFile(file:env.OutputShare+"\\"+ env.OutputShareAB+"\\version.txt", text: Utility.ObjToJsonStr(manifestVersion,true))
                        }
                    }
                }
            }
        }
        post{
            always {
                script{
                    node(BuildArgs.AssignedLabel){
                        Utility.UpdateJobStatusOnELK(this)
                        Utility.AddJobResultOnELK(this)
                        Utility.CleanJobWorkSpace(this)
                        DingSender.SendDingMSG(this,env.DingRobot)
                        try{
                            EmailSender.SendEmail(this,env.MailTo,scmRS)
                        }
                        catch(Exception ex){
                            EmailSender.SendEmail(this,env.MailTo)
                        }
                    }
                    
                }
            }
        }
    }
}