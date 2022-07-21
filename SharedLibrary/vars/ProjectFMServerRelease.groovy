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
                        }
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        env.SCMStr = ""
                        currentBuild.description = "${env.BUILD_NUMBER}_${env.version}"
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull != false }}
                steps{
                    script{
                        stage('Git Pull Server') {
                            node(BuildArgs.AssignedLabel_Server){
                                ws(env.GitRoot){
                                    def buildargs = [:] << BuildArgs
                                    scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                    dir(env.LinkScriptWS){
                                        bat("""${env.LinkScript} """)
                                    }
                                }
                                gitLogM1([env.GitRoot],"Server",BuildArgs.Submodule)
                            }
                        }
                        if(scmRS.size() > 0){
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
                        node(BuildArgs.AssignedLabel_Server){
                            print(NODE_NAME)
                            try{
                                def outputRoot = "${env.GitRootServer}\\${env.OutputFolderRoot}\\server"
                                cleanOutput(outputRoot)
                                stage('Build Server') {
                                    // clean previous build output
                                    cleanOutput(env.GitRootServer+"\\ServerBin")
                                    dir("${env.GitRootServer}"){
                                        timeout(5){ // adding 5 min timeout to fail the build if the cmd gets stuck
                                            //msbuild("""${env.ServerSln} ${env.ServerBuildOpt}""", env.MSBuild)
                                            devenv(solution:env.ServerSln, opt: env."${params.version}.ServerBuildOpt")
                                        }
                                    }
                                    gitStatus([env.GitRoot],"Server", BuildArgs.Submodule)                  
                                }
                                stage('Release Server') {
                                    dir("${env.GitRootServer}"){
                                        
                                        bat(env."${params.version}.ServerRelease")
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
                                    if(params.UploadServer){
                                        try{
                                            def zipName = "${outputRoot}\\Server.zip"
                                            compress(zip:zipName, path:"${outputRoot}\\Server", mode:"1",relpath:"..")
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