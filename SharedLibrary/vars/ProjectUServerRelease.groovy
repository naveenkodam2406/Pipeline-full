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
                        currentBuild.description = "${env.BUILD_NUMBER}_${env.server}_${env.UpdateServerTag}"
                        env.Options.split(" ").each{
                            if(it.contains("versionCode")){
                                def tmp = it.split("#")[1].trim()
                                currentBuild.description += "_${tmp}"
                            }
                            else if(it.contains("bundleVersionStep")){
                                currentBuild.description += "_${it}"
                            }
                        }
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull == true }}
                steps{
                    script{
                        parallel(
                            'Git Pull Client':{
                                stage('Git Pull Client') {
                                    node(BuildArgs.AssignedLabel_Android){
                                        ws(env.GitRootAndroid){
                                            def buildargs = [:] << BuildArgs
                                            scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                            projectULinkFramework(env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Framework)
                                            dir(env.LinkScriptWS){
                                                bat("""${env.LinkScript} """)
                                            }
                                        }
                                    }
                                }
                            },
                            'Git Pull Server':{
                                stage('Git Pull Server') {
                                    node(BuildArgs.AssignedLabel_Server){
                                        dir(env.GitRootServer){
                                            def buildargs = [:] << BuildArgs
                                            buildargs.remove("MultiScm") // remove client conf
                                            scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                        }
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
                        node(BuildArgs.AssignedLabel_Server){
                            print(NODE_NAME)
                            try{
                                def outputRoot = "${env.GitRootServer}\\${env.OutputFolderRoot}\\server"
                                
                                stage('Build Server') {
                                    if(params.BuildServer == true ){
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
                                        dir(outputRoot){deleteDir()}
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
                                    if(params.BuildServer == true ){
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
                                                currentBuild.description += "<br/>ftp://${ip}/"+remote+"/${env.PackageName}/Server"
                                            }
                                        }}
                                    }
                                    else{
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                                stage('Upload Server') {
                                    try{
                                        if(params.UploadServer == true){
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