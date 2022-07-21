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
                        node(BuildArgs.AssignedLabel){
                            Utility.UpdateJobStatusOnELK(this)
                            //DingSender.SendDingMSG(this,env.DingRobot,"STARTED")
                        }
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        env.Options = env.Options.replace("#false","#0").replace("#true","#1")
                        env.SCMStr = ""
                        manifestVersion["ClientVersion"] = env.versionCode
                        currentBuild.description = "${env.BUILD_NUMBER}_${env.versionPrefix}"
                        env.Options.split(" ").each{
                            if(it.contains("version#")){
                                def tmp = it.split("#")[1].trim()
                                currentBuild.description += "_${tmp}"
                            }
                            else if(it.contains("versionCode#")){
                                def tmp = it.split("#")[1].trim()
                                currentBuild.description += "_${tmp}_JustAB#${params.JustBuildAB}" // just ab follows with versionCode
                            }
                            else if(it.contains("bundleVersionStep#")){
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
                        env.OutputABHotfixTarget = env.OutputABHotfixTarget.replace("_{PLACEHOLDER}",suffix)
                        currentBuild.description += "<br/>" + env.OutputShare
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull != false }}
                steps{
                    script{
                        parallel(
                            'Git Pull Server':{
                                stage('Git Pull Server') {
                                    if(params.BuildServer == true){
                                        node(BuildArgs.AssignedLabel_Server){
                                            dir(env.GitRootServer){
                                                def buildargs = Utility.JsonStrToObj(Utility.ObjToJsonStr(BuildArgs))
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule)) // sync for android
                                                //always align with Server branch, at frist
                                                buildargs.MultiScm.each{ conf ->l:{
                                                    if(params.server != conf.Branches[0]["name"]){ // switch for server, if the branch doesn't match
                                                        dir(conf.RelativeTargetDir){
                                                            gitSwitchBranch(params.server)
                                                        }
                                                    }
                                                }}
                                                projectULinkFramework(env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Framework)
                                                dir(env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Client){
                                                    bat(returnStdout: true, script:Utility.Git.CurrentBranch.CMD+">"+env.GitRootServer+"\\ServerGitPull.log")
                                                    bat(returnStdout: true, script:Utility.Git.LogM1.CMD+">>"+env.GitRootServer+"\\ServerGitPull.log")
                                                }
                                                dir(env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Framework){
                                                    bat(returnStdout: true, script:Utility.Git.CurrentBranch.CMD+">>"+env.GitRootServer+"\\ServerGitPull.log")
                                                    bat(returnStdout: true, script:Utility.Git.LogM1.CMD+">>"+env.GitRootServer+"\\ServerGitPull.log")
                                                }
                                                dir(env.LinkScriptWS){
                                                    bat("""${env.LinkScript} """)
                                                }
                                                buildargs = Utility.JsonStrToObj(Utility.ObjToJsonStr(BuildArgs))
                                                buildargs.remove("MultiScm") // remove client conf
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                dir(env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Server){
                                                    bat(returnStdout: true, script:Utility.Git.CurrentBranch.CMD+">>"+env.GitRootServer+"\\ServerGitPull.log")
                                                    bat(returnStdout: true, script:Utility.Git.LogM1.CMD+">>"+env.GitRootServer+"\\ServerGitPull.log")
                                                }
                                                print readFile("ServerGitPull.log")
                                                archiveArtifacts artifacts: "ServerGitPull.log", followSymlinks: false, fingerprint: true, allowEmptyArchive : true
                                            }
                                            
                                        }
                                    }
                                    else
                                    {
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            },
                            "Git Pull Windows":{
                                stage('Git Pull Windows') {
                                    if(params.Windows == true){
                                        node(BuildArgs.AssignedLabel_Win){
                                            ws(env.GitRootWin){}
                                            dir(env.GitRootWin){
                                                def buildargs = Utility.JsonStrToObj(Utility.ObjToJsonStr(BuildArgs))
                                                print buildargs.MultiScm
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                projectULinkFramework(env.GitRootWin+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootWin+"\\"+BuildArgs.CheckOutFolder.Framework)
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
                            'Git Pull IOS':{
                                stage('Git Pull IOS') {
                                    if(params.IOS != false){
                                        node(BuildArgs.AssignedLabel_IOS){
                                            ws(env.GitRootIOS){}
                                            dir(env.GitRootIOS){
                                                def buildargs = Utility.JsonStrToObj(Utility.ObjToJsonStr(BuildArgs))
                                                print buildargs.MultiScm
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                projectULinkFramework(env.GitRootIOS+"/"+BuildArgs.CheckOutFolder.Client,env.GitRootIOS+"/"+BuildArgs.CheckOutFolder.Framework)
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
                            Utility.SetDisplayName(scmRS, currentBuild, scmList)
                            def elkscmdata = [:]
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
                                    if(params.IOS == true){
                                        node(BuildArgs.AssignedLabel_IOS){
                                            print(NODE_NAME)
                                            def outputRoot = Utility.OS(this).Path("${env.GitRootIOS}/${env.OutputFolderRoot}")
                                            def xcodeProjPath = "${outputRoot}/${env.IOSProjectName}"
                                            def xcodeArchivePath = "${outputRoot}/${env.IOSProjectName}_archive"
                                            def mountedFolder = null
                                            try{
                                                mountedFolder = mount(path:env.OutputShare, cred:env.MountCred)
                                                dir(env.GitRootIOS +"/"+env.ProjectPathClient+"/ResourceCheckResult"){
                                                    deleteDir()
                                                }
                                                withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(env.MacBuildAccount), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                        sh("""security unlock-keychain -p ${pwd}""")
                                                    }
                                                stage('Build IOS') {
                                                    if(params.BuildClient == true || params.JustBuildAB == true ){
                                                        // clean previous build output
                                                        dir("${outputRoot}"){deleteDir()}
                                                        overwriteBundleData("BundleDataiOS.asset", env.GitRootIOS +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                        unityExec(unityExePath:env.UnityPathIOS, projectPath:env.GitRootIOS +"/"+env.ProjectPathClient, 
                                                            executeMethod:env.ExecBuildIOS, options:env.Options, output:outputRoot, apk:env.IOSProjectName,
                                                            logKeyWords:BuildArgs.UnityErrorCheck,
                                                            last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,
                                                            buildAssetBundle:env.ExecBuildABIOS)
                                                        sh("mkdir -p ${outputRoot}/IOS")
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Export iOS"){
                                                    if(params.Release == true && ( params.BuildClient == true && params.JustBuildAB == false)){
                                                        uFullExportIOS(outputRoot:outputRoot, 
                                                            mounted:mountedFolder,
                                                            plistResourceFolder: "gameClientSettings\\projectU\\${env.Region}",
                                                            xcodeProjPath:xcodeProjPath,
                                                            xcodeArchivePath: xcodeArchivePath)
                                                        sh("mkdir -p ${mountedFolder[0]}/${env.OutputShareClient}")
                                                        releaseEAB(GitRoot:env.GitRootIOS, OutputRoot:"${outputRoot}/IOS")
                                                        rsync("${outputRoot}/IOS", "${mountedFolder[0]}/${env.OutputShareClient}")
                                                        sh("find ${mountedFolder[0]} -name '.DS_Store' -type f -delete")
                                                        mdFive(path:outputRoot+"/IOS", relpath:"..", python:"/usr/local/bin/python")
                                                        clientBuildMD5 += readFile("${outputRoot}/IOS/VerifyMd5.txt")
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release IOS AB"){
                                                    if(params.Release == true && (params.BuildClient == true || params.JustBuildAB == true)){
                                                        def tmp = releaseAB(GitRoot:env.GitRootIOS, OutputRoot: outputRoot,MountedFolder:mountedFolder[0])
                                                        clientAbMD5 += "${tmp}"
                                                        manifestVersion["iOSBundleVersion"] = readFile(env.GitRootIOS +"/"+env.ProjectPathClient+"/AssetBundles/iOS/BundleDataVersion.txt").tokenize(",")[0]
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
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
                                                def outputRoot = Utility.OS(this).Path("${env.GitRootServer}\\${env.OutputFolderRoot}\\server")
                                                stage('Build Server') {
                                                    try{
                                                        dir(env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Server){
                                                            bat(""" echo ${env.GitRootAndroid}\\${BuildArgs.CheckOutFolder.Client}\\trunk\\CommonProject| link_common_project.bat""")
                                                            projectUServerLinkFramework(env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Server,env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Framework)
                                                        }
                                                    }
                                                    catch(Exception ex){
                                                        print ex
                                                    }
                                                    dir(outputRoot){deleteDir()}
                                                    dir("${env.GitRootServer}\\Server"){
                                                        dir("Bin"){deleteDir()}
                                                        timeout(10){
                                                            msbuild("""${env.ServerSln} ${env.ServerBuildOpt}""", env.MSBuild)
                                                        }
                                                    }
                                                }
                                                stage('Release Server') {
                                                    dir("${env.GitRootServer}\\Server"){
                                                        bat(env.ServerRelease)
                                                    }
                                                    def ver = Utility.ObjToJsonStr([ServerVersion:env.BuildId],true)
                                                    writeFile(file: env.GitRootServer+"\\server\\Bin\\version.txt", text:ver)
                                                    mdFive(path:env.GitRootServer+"\\server\\Bin")
                                                    robocopy(env.GitRootServer+"\\server\\Bin","${outputRoot}\\Server","/MIR")
                                                    compress(zip:"${outputRoot}\\Server.zip", 
                                                        path:"${outputRoot}\\Server", mode:"1",relpath:"..")
                                                    robocopy("${outputRoot}\\Server",env.OutputShare+"\\"+env.OutputShareServer,"/MIR")
                                                }
                                                stage('Upload Server') {
                                                    if(params.UploadServer == true){
                                                        try{
                                                            print ("Upload Server ${outputRoot}\\Server.zip")
                                                            if(BuildArgs.ServerUploadFTP){
                                                                uploadServer(path:outputRoot, file: "Server.zip", serverConfPath:BuildArgs.ServerUploadFTP, serverTag:env.UpdateServerTag)
                                                            }
                                                        }
                                                        catch(Exception ex){
                                                        // set unstable not fail the build
                                                            unstable(message: "Upload to FTP is unstable")
                                                            print ex
                                                        }
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
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
                                stage("Android"){
                                    if(params.Android == true ){
                                        node(BuildArgs.AssignedLabel_Android){
                                            print(NODE_NAME)
                                            stage("Git Pull Android"){
                                                if( params.GitPull == true ){
                                                    ws(env.GitRootAndroid){}
                                                    dir(env.GitRootAndroid){
                                                        def buildargs = Utility.JsonStrToObj(Utility.ObjToJsonStr(BuildArgs))
                                                        //always align with Server branch, at frist
                                                        // buildargs.MultiScm.each{ conf ->l:{
                                                        //     if(params.server != conf.Branches[0]["name"]){ // back to proper branch for game and framework
                                                        //         dir(conf.RelativeTargetDir){
                                                        //             gitSwitchBranch(conf.Branches[0]["name"])
                                                        //         }
                                                        //     }
                                                        // }}
                                                        // print buildargs.MultiScm
                                                        GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule)
                                                        dir(env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Client){
                                                            bat(returnStdout: true, script:Utility.Git.CurrentBranch.CMD+">"+env.GitRootAndroid+"\\AndroidGitPull.log")
                                                            bat(returnStdout: true, script:Utility.Git.LogM1.CMD+">>"+env.GitRootAndroid+"\\AndroidGitPull.log")
                                                        }
                                                        dir(env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Framework){
                                                            bat(returnStdout: true, script:Utility.Git.CurrentBranch.CMD+">>"+env.GitRootAndroid+"\\AndroidGitPull.log")
                                                            bat(returnStdout: true, script:Utility.Git.LogM1.CMD+">>"+env.GitRootAndroid+"\\AndroidGitPull.log")
                                                        }
                                                        print readFile("AndroidGitPull.log")
                                                        archiveArtifacts artifacts: "AndroidGitPull.log", followSymlinks: false, fingerprint: true, allowEmptyArchive : true
                                                        projectULinkFramework(env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Framework)
                                                        dir(env.LinkScriptWS){
                                                            bat("""${env.LinkScript} """)
                                                        }
                                                    }
                                                }
                                                else{
                                                    Utility.MarkStageAsSkipped(STAGE_NAME)
                                                }
                                            }
                                            try{
                                                dir(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                                                    deleteDir()
                                                }
                                                def outputRoot = Utility.OS(this).Path("${env.GitRootAndroid}\\${env.OutputFolderRoot}\\android")
                                                stage('Build Android') {
                                                    if(params.BuildClient == true|| params.JustBuildAB == true){
                                                        // clean previous build output
                                                        dir(outputRoot){deleteDir()}
                                                        overwriteBundleData("BundleDataAndroid.asset", env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                        unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootAndroid +"\\"+env.ProjectPathClient, 
                                                            executeMethod:env.ExecBuildAndroid, options:env.Options, output:outputRoot, apk:"Android\\"+env.ApkName,
                                                            logKeyWords:BuildArgs.UnityErrorCheck,
                                                            last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:env.ApkName,
                                                            buildAssetBundle:env.ExecBuildABAndroid)
                                                        compress(zip:"${outputRoot}\\Android\\Resave.zip" , path:env.GitRootAndroid +"\\"+env.ResaveFolder)
                                                        releaseEAB(GitRoot:env.GitRootAndroid, OutputRoot:"${outputRoot}\\Android")
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage('Build OBB') {
                                                    if((params.BuildClient == true && params.BuildOBB == true) && params.JustBuildAB != true ){
                                                        if(env.Region != "TW"){
                                                            unstable(message: "Only TW Region is permitted obb")
                                                        }
                                                        else{
                                                            robocopy(env.GitRootAndroid +"\\"+ env.ProjectPathClient + "\\Assets\\StreamingAssets","StreamingAssets")
                                                            dir(env.GitRootAndroid+"\\"+ env.ProjectPathClient){
                                                                gitClean(env.GitRootAndroid +"\\"+ env.ProjectPathClient + "\\Assets\\StreamingAssets", "-x")
                                                            }
                                                            unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootAndroid +"\\"+env.ProjectPathClient, 
                                                                executeMethod:env.ExecBuildAndroid_OBB, options:env.Options, output:"${outputRoot}\\OBB", apk:"${env.MiniApkName}",
                                                                logKeyWords:BuildArgs.UnityErrorCheck,
                                                                last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true, checkOutput:env.MiniApkName,
                                                                buildAssetBundle:env.ExecBuildABAndroid)
                                                            env.ObbExclude += " " + readFile(env.GitRootAndroid +"\\"+ env.ObbExcludeFile).tokenize("\r\n").join(" ")
                                                            robocopy(outputRoot+"\\OBB", outputRoot+"\\Android"," ${env.MiniApkName} ")
                                                            obbDiffList(fullApk:"${outputRoot}\\Android\\${env.ApkName}", obbApk: "${outputRoot}\\Android\\${env.MiniApkName}",
                                                                resave: "${outputRoot}\\Android\\Resave.zip", exclude: env.ObbExclude, out: "${outputRoot}\\Android\\OBB_APK_Diff.txt")
                                                        }
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release Android") {
                                                    if(params.Release == true && ( params.BuildClient == true && params.JustBuildAB == false)){
                                                        mdFive(path:outputRoot+"\\Android",relpath:"..")
                                                        clientBuildMD5 += readFile("${outputRoot}\\Android\\VerifyMd5.txt")
                                                        robocopy(outputRoot+"\\Android",env.OutputShare+"\\"+env.OutputShareClient+"\\Android"," /XF VerifyMd5.txt /E /S")
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release Android AB"){
                                                    if(params.Release == true && (params.BuildClient == true || params.JustBuildAB == true)){
                                                        def tmp = releaseAB(GitRoot:env.GitRootAndroid, OutputRoot: outputRoot)
                                                        clientAbMD5 += "${tmp}"
                                                        manifestVersion["AndroidBundleVersion"] = readFile(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\AssetBundles\\Android\\BundleDataVersion.txt").tokenize(",")[0]
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
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
                            "Windows":{
                                stage("Windows"){
                                    if(params.Windows == true ) {
                                        node(BuildArgs.AssignedLabel_Win){
                                            print(NODE_NAME)
                                            try{
                                                def outputRoot = Utility.OS(this).Path("${env.GitRootWin}\\${env.OutputFolderRoot}\\windows")
                                                dir(env.GitRootWin +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                                                    deleteDir()
                                                }
                                                stage('Build Windows') {
                                                    if(params.BuildClient == true || params.JustBuildAB == true){
                                                        overwriteBundleData("BundleDataStandaloneWindows64.asset", env.GitRootWin +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                        // clean previous build output
                                                        dir(outputRoot){deleteDir()}
                                                        unityExec(unityExePath:env.UnityPathWin, projectPath:env.GitRootWin +"\\"+env.ProjectPathClient, 
                                                                executeMethod:env.ExecBuildWin, options:env.Options, output:outputRoot, apk:env.ProductName +"\\"+env.WinName,
                                                                logKeyWords:BuildArgs.UnityErrorCheck,
                                                                last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:env.WinName,
                                                                buildAssetBundle:env.ExecBuildABWin)
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage('Release Windows') {
                                                    if(params.Release == true && ( params.BuildClient == true && params.JustBuildAB == false)){
                                                        compress(zip:"${outputRoot}\\Windows\\${env.ProductName}.zip", 
                                                        path:"${outputRoot}\\${env.ProductName}", mode:"1",relpath:"..")
                                                        mdFive(path:outputRoot+"\\Windows", relpath:"..")
                                                        clientBuildMD5 += readFile("${outputRoot}\\Windows\\VerifyMd5.txt")
                                                        robocopy("${outputRoot}\\Windows", env.OutputShare+"\\"+env.OutputShareClient+"\\Windows" ," ${env.ProductName}.zip ")
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release Windows AB"){
                                                    if(params.Release == true && (params.BuildClient == true || params.JustBuildAB == true)){
                                                        def tmp = releaseAB(GitRoot:env.GitRootWin, OutputRoot: outputRoot)
                                                        clientAbMD5 += "${tmp}"
                                                        manifestVersion["WindowsBundleVersion"] = readFile(env.GitRootWin +"\\"+env.ProjectPathClient+"\\AssetBundles\\StandaloneWindows64\\BundleDataVersion.txt").tokenize(",")[0]
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
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
                            failFast:env.FailFast == "true"?true:false
                        )
                    }
                }
            }
            stage("Release"){
                when{expression { params.Release == true }}
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            if(clientBuildMD5 != ""){
                                writeFile(file:env.OutputShare+"\\"+env.OutputShareClient+"\\VerifyMd5.txt", text: clientBuildMD5)
                            }
                            if(params.BuildClient == true || params.JustBuildAB == true){
                                compress(zip:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}.zip" , 
                                path:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}", 
                                relpath:".", mode:1)
                                dir(env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}"){
                                    deleteDir()
                                }
                                mdFive(file:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}.zip")
                                clientAbMD5 += env.md5 + " .\\manifest_${env.BUILD_NUMBER}.zip"
                                writeFile(file:env.OutputShare+"\\"+env.OutputShareAB+"\\VerifyMd5.txt", text: clientAbMD5, encoding:"UTF-8")
                                writeFile(file:env.OutputShare+"\\"+env.OutputShareAB+"\\version.txt", text: Utility.ObjToJsonStr(manifestVersion,true))
                            }
                        }
                        if(params.UpdateHoxfix==true){
                            if(BuildArgs.AssignedLabel_ABHotfix){
                                node(BuildArgs.AssignedLabel_ABHotfix){
                                    robocopy(env.OutputABHotfixTarget+"\\"+env.OutputShareAB, env.OutputABHotfix, "/XF *.zip /E /S")
                                    Utility.CleanParallelJobWorkSpace(this)
                                }
                            }
                            else{
                                robocopy(env.OutputShare+"\\"+env.OutputShareAB, env.OutputABHotfix, "/XF *.zip /E /S")
                            }
                            
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
            success{
                script{
                    try{
                        EmailSender.SendUReportEmail(this,env.ReportReceiver, manifestVersion)
                    }
                    catch(Exception ex){
                        print ex
                    }
                    
                }
            }
        }
    }
}