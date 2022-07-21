import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    def scmRS = []
    def scmList = []
    def clientBuildMD5 = ""
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
                        }
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        env.PackageName = Utility.ReplaceSlash(env.PackageName)
                        env.Options = env.Options.replace("#false","#0").replace("#true","#1")
                        env.SCMStr = ""
                        currentBuild.description = "${env.BUILD_NUMBER}_${env.version}_${env.versionPrefix}"
                        env.Options.split(" ").each{
                            if(it.contains("versionCode")){
                                def tmp = it.split("#")[1].trim()
                                currentBuild.description += "_${tmp}"
                            }
                            else if(it.contains("bundleVersionStep")){
                                currentBuild.description += "_${it}"
                            }
                        }
                        if(BuildArgs.ClientUploadFTP){
                            BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                currentBuild.description +="<br/>ftp://" + ip + remote + "/${env.PackageName}"
                            }}
                        }
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull == true }}
                steps{
                    script{
                        parallel(
                            "Git Pull Windows":{
                                stage('Git Pull Windows') {
                                    if(params.Windows == true || params.BuildServer == true ){
                                        node(BuildArgs.AssignedLabel_Win){
                                            ws(env.GitRootWin){}
                                            dir(env.GitRootWin){
                                                def buildargs = [:] << BuildArgs
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
                            'Git Pull Android':{
                                stage('Git Pull Android') {
                                    if(params.Android != null && (params.Android == true || params.BuildServer == true )){
                                        node(BuildArgs.AssignedLabel_Android){
                                            ws(env.GitRootAndroid){}
                                            dir(env.GitRootAndroid){
                                                def buildargs = [:] << BuildArgs
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                projectULinkFramework(env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Framework)
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
                            'Git Pull Server':{
                                stage('Git Pull Server') {
                                    // if(params.BuildServer == true ){
                                        if(env.GitRootServer != null){
                                            node(BuildArgs.AssignedLabel_Server){
                                                dir(env.GitRootServer){
                                                    def buildargs = [:] << BuildArgs
                                                    buildargs.remove("MultiScm") // remove client conf
                                                    scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                }
                                            }
                                        }
                                    // }
                                    // else
                                    // {
                                    //     Utility.MarkStageAsSkipped(STAGE_NAME)
                                    // }
                                }
                            },
                            'Git Pull IOS':{
                                stage('Git Pull IOS') {
                                    if(params.IOS == true ){
                                        node(BuildArgs.AssignedLabel_IOS){
                                            ws(env.GitRootIOS){}
                                            dir(env.GitRootIOS){
                                                def buildargs = [:] << BuildArgs
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
                            Utility.SetDisplayName(scmRS, currentBuild, scmList)
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
                                    if(params.IOS == true && params.BuildClient == true ){
                                        node(BuildArgs.AssignedLabel_IOS){
                                            print(NODE_NAME)
                                            def outputRoot = "${env.GitRootIOS}/${env.OutputFolderRoot}"
                                            def xcodeProjPath = "${outputRoot}/${env.IOSProjectName}"
                                            def xcodeArchivePath = "${outputRoot}/${env.IOSProjectName}_archive"
                                            try{
                                                overwriteBundleData("BundleDataiOS.asset", env.GitRootIOS +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                dir(env.GitRootIOS +"/"+env.ProjectPathClient+"/ResourceCheckResult"){
                                                    deleteDir()
                                                }
                                                withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(env.MacBuildAccount), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                    sh("""security unlock-keychain -p ${pwd}""")
                                                }
                                                stage('Build IOS') {
                                                    if(params.BuildClient == true  ){
                                                        // clean previous build output
                                                        dir(outputRoot){deleteDir()}
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
                                                    if(params.JustBuildAB != true){
                                                        parallel(
                                                            "Compress":{
                                                                stage("Compress"){
                                                                    if(params.UploadXcodeProject){
                                                                        compress(zip:"${outputRoot}/IOS/xcode_${env.PackageName}.zip",path:"${xcodeProjPath}", mode:"1" , python:"/usr/local/bin/python")
                                                                    }
                                                                }
                                                            },
                                                            "ZiHao_QA":{
                                                                stage("ZiHao_QA"){
                                                                    iosExport("xcodeProjPath": xcodeProjPath, "xcodeArchivePath": xcodeArchivePath, plist:env.Plist,
                                                                        plistPath: env.PlistPath, "outputRoot": outputRoot, discipline: "ZiHao_QA",
                                                                        bundleId: env.BundleId, teamId: env.TeamId,
                                                                        codeName: env.CodeName
                                                                    )
                                                                }
                                                            }
                                                        )
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release IOS"){
                                                    def ipaName = env.IOSName.replace("{Discipline}", "ZiHao_QA")
                                                    try{
                                                        compress(zip:"${outputRoot}/AssetBundlIOS.zip",
                                                        path:env.GitRootIOS +"/"+env.ProjectPathClient+"/AssetBundles", mode:"1",relpath:".",python:"/usr/local/bin/python")
                                                    }
                                                    catch(Exception compEx)
                                                    {
                                                        print compEx
                                                    }
                                                    if(BuildArgs.ClientUploadFTP){
                                                        BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                                            withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                                ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/ios", path:"${outputRoot}/IOS/${ipaName}")
                                                                ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/ios", path:env.GitRootIOS +"/"+env.ResaveFolder)
                                                                if(params.UploadXcodeProject){
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/ios", path:"${outputRoot}/IOS/xcode_${env.PackageName}.zip")
                                                                }
                                                                try{
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/UpdateClientData", path:"${outputRoot}/AssetBundlIOS.zip")
                                                                }
                                                                catch(Exception ex)
                                                                {
                                                                    print ex
                                                                }
                                                                if(shTestPath(env.GitRootIOS +"/"+env.ProjectPathClient+"/ExportAssetBundle")){
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/ios", path:env.GitRootIOS +"/"+env.ProjectPathClient+"/ExportAssetBundle")
                                                                }
                                                            }
                                                        }}
                                                    }
                                                    if(params.UpdateHoxfix==true){
                                                        def mountedFolder = null
                                                        try{
                                                            mountedFolder = mount(path:env.OutputABHotfix, cred:env.MountCred)
                                                            rsync(env.GitRootIOS +"/"+env.ProjectPathClient+"/AssetBundles/", "${mountedFolder[0]}","*/","")
                                                        }   
                                                        catch(Exception ex)
                                                        {
                                                            print ex
                                                        }
                                                        finally{
                                                            unmount(mountedFolder)
                                                        }
                                                    }
                                                }
                                            }
                                            finally{
                                                archiveFolder(env.GitRootIOS +"\\"+env.ProjectPathClient+"\\ResourceCheckResult")
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
                                    node(BuildArgs.AssignedLabel_Server){
                                        print(NODE_NAME)
                                        try{
                                            def outputRoot = "${env.GitRootServer}\\${env.OutputFolderRoot}\\server"
                                            stage('Build Server') {
                                                if(params.BuildServer == true ){
                                                    dir(outputRoot){deleteDir()}
                                                    try{
                                                        dir(env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Server){
                                                            bat(""" echo ${env.GitRootAndroid}\\${BuildArgs.CheckOutFolder.Client}\\trunk\\CommonProject| link_common_project.bat""")
                                                            projectUServerLinkFramework(env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Server,env.GitRootServer+"\\"+BuildArgs.CheckOutFolder.Framework)
                                                        }
                                                    }
                                                    catch(Exception ex){
                                                        print ex
                                                    }
                                                    // clean previous build output
                                                    dir("${env.GitRootServer}\\Server"){
                                                        dir("Bin"){deleteDir()}
                                                        timeout(10){
                                                            msbuild("""${env.ServerSln} ${env.ServerBuildOpt}""", env.MSBuild)
                                                        }
                                                    }
                                                    
                                                    // mdFive(path:env.GitRootServer+"\\server\\Bin")
                                                }
                                                else{
                                                    Utility.MarkStageAsSkipped(STAGE_NAME)
                                                }
                                            }
                                            stage('Release Server') {
                                                if( params.BuildServer == true ){
                                                    dir("${env.GitRootServer}\\Server"){
                                                        bat(env.ServerRelease)
                                                    }
                                                    def ver = Utility.ObjToJsonStr([ServerVersion:env.BuildId],true)
                                                    writeFile(file: env.GitRootServer+"\\server\\Bin\\version.txt", text:ver)
                                                    robocopy(env.GitRootServer+"\\server\\Bin","${outputRoot}\\Server","/MIR")
                                                    compress(zip:"${outputRoot}\\Server.zip",
                                                            path:"${outputRoot}\\Server", mode:"1",relpath:"..")
                                                    BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                                        withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                            ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/Server", path:"${outputRoot}\\Server.zip")
                                                        }
                                                    }}
                                                }
                                                else{
                                                    Utility.MarkStageAsSkipped(STAGE_NAME)
                                                }
                                            }
                                            stage('Upload Server') {
                                                try{
                                                    if(params.UploadServer == true && params.BuildServer == true ){
                                                        print ("Upload Server ${outputRoot}\\Server.zip")
                                                        if(BuildArgs.ServerUploadFTP){
                                                            retry(2){
                                                                uploadServer(path:outputRoot, file: "Server.zip", serverConfPath:BuildArgs.ServerUploadFTP, serverTag:env.UpdateServerTag)
                                                            }
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
                            },
                            "Windows":{
                                stage("Windows"){
                                    if(params.Windows == true && params.BuildClient == true ){
                                        node(BuildArgs.AssignedLabel_Win){
                                            print(NODE_NAME)
                                            try{
                                                def outputRoot = "${env.GitRootWin}\\${env.OutputFolderRoot}\\windows"
                                                dir(env.GitRootWin +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                                                    deleteDir()
                                                }
                                                stage('Build Windows') {
                                                    if(params.BuildClient == true ){
                                                        overwriteBundleData("BundleDataStandaloneWindows64.asset", env.GitRootWin +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                        // clean previous build output
                                                        dir(outputRoot){deleteDir()}
                                                        unityExec(unityExePath:env.UnityPathWin, projectPath:env.GitRootWin +"\\"+env.ProjectPathClient, 
                                                                executeMethod:env.ExecBuildWin, options:env.Options, output:outputRoot, apk:env.ProductName+"\\"+env.WinName,
                                                                logKeyWords:BuildArgs.UnityErrorCheck,
                                                                last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:env.WinName,
                                                                buildAssetBundle:env.ExecBuildABWin)
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage('Release Windows') {
                                                    compress(zip:"${outputRoot}\\${env.PackageName}.zip",
                                                    path:"${outputRoot}\\${env.ProductName}", mode:"1",relpath:"..")
                                                    compress(zip:"${outputRoot}\\AssetBundleWindows.zip",
                                                    path:env.GitRootWin +"\\"+env.ProjectPathClient+"\\AssetBundles", mode:"1",relpath:".")
                                                    if(BuildArgs.ClientUploadFTP){
                                                        BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                                            withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                                ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/windows", path:"${outputRoot}\\${env.PackageName}.zip")
                                                                try{
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/UpdateClientData", path:"${outputRoot}\\AssetBundleWindows.zip")
                                                                }
                                                                catch(Exception ex)
                                                                {
                                                                    print ex
                                                                }
                                                            }
                                                        }}
                                                    }
                                                    if(params.UpdateHoxfix==true){
                                                        try{
                                                            robocopy(env.GitRootWin +"\\"+env.ProjectPathClient+"\\AssetBundles", env.OutputABHotfix, "/E /S")
                                                        }   
                                                        catch(Exception ex)
                                                        {
                                                            print ex
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
                                                dir(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                                                    deleteDir()
                                                }
                                                def outputRoot = "${env.GitRootAndroid}\\${env.OutputFolderRoot}\\android"
                                                stage('Build Android') {
                                                    if(params.BuildClient == true ){
                                                        // clean previous build output
                                                        dir(outputRoot){deleteDir()}
                                                        unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootAndroid +"\\"+env.ProjectPathClient, 
                                                            executeMethod:env.ExecBuildAndroid, options:env.Options, output:outputRoot, apk:env.ProductName+"\\"+env.ApkName,
                                                            logKeyWords:BuildArgs.UnityErrorCheck,
                                                            last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:env.ApkName,
                                                            buildAssetBundle:env.ExecBuildABAndroid)
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release Android") {
                                                    if( params.JustBuildAB != true){
                                                        //compress(zip:"${outputRoot}\\${env.ProductName}\\Resave.zip" , path:env.GitRootAndroid +"\\"+env.ResaveFolder)
                                                        compress(zip:"${outputRoot}\\AssetBundlAndroid.zip",
                                                        path:env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\AssetBundles", mode:"1",relpath:".")
                                                        if(BuildArgs.ClientUploadFTP){
                                                            BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                                                withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/android", path:"${outputRoot}/${env.ProductName}/${env.ApkName}")
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/android", path:env.GitRootAndroid +"\\"+env.ResaveFolder)
                                                                    try{
                                                                        ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/UpdateClientData", path:"${outputRoot}\\AssetBundlAndroid.zip")
                                                                    }
                                                                    catch(Exception ex)
                                                                    {
                                                                        print ex
                                                                    }
                                                                    if(psTestPath(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\ExportAssetBundle")){
                                                                        ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/${env.PackageName}/android", path:env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\ExportAssetBundle")
                                                                    }
                                                                }
                                                            }}
                                                        }
                                                        if(params.UpdateHoxfix==true){
                                                            try{
                                                                robocopy(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\AssetBundles", env.OutputABHotfix,  "/E /S")
                                                            }   
                                                            catch(Exception ex)
                                                            {
                                                                print ex
                                                            }
                                                        }
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
                            failFast:env.FailFast == "true"?true:false
                        )
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