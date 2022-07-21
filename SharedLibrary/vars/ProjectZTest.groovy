import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    def scmRS = []
    def scmList = []
    def clientBuildMD5 = ""
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
                        checkDiskFreeSize(BuildArgs.DiskSpaceCheck)
                        node(BuildArgs.AssignedLabel){
                            Utility.UpdateJobStatusOnELK(this)
                        }
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        env.Options = env.Options.replace("#false","#0").replace("#true","#1")
                        env.SCMStr = ""
                        currentBuild.description = "${env.BUILD_NUMBER}_${env.versionPrefix}"
                        env.Options.split(" ").each{
                            if(it.contains("version#")){
                                def tmp = it.split("#")[1].trim()
                                currentBuild.description += "_${tmp}"
                            }
                            else if(it.contains("versionCode#")){
                                def tmp = it.split("#")[1].trim()
                                env.versionCode = tmp
                                currentBuild.description += "_${tmp}"
                            }
                        }
                        manifestVersion["ClientVersion"] = env.versionCode
                        currentBuild.description += "_${params.Profiler}"
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull != false }}
                steps{
                    script{
                        switch(params.Profiler.toLowerCase()){
                            case ~/poco/:
                                node(BuildArgs.AssignedLabel_Android){
                                    ws(env.GitRootPoco){
                                        def buildargs = [:] << BuildArgs
                                        scmRS.add(GitPipelineV2.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                        bat("""${env.LinkScript} """)
                                    }
                                    gitLogM1([env.GitRootPoco],"poco",BuildArgs.Submodule)
                                }
                            break;
                            case ~/uwagot/:
                                node(BuildArgs.AssignedLabel_Android){
                                    ws(env.GitRootUwa){
                                        def buildargs = [:] << BuildArgs
                                        scmRS.add(GitPipelineV2.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                        bat("""${env.LinkScript} """)
                                    }
                                    gitLogM1([env.GitRootUwa],"uwagot",BuildArgs.Submodule)
                                }
                            break;
                        }
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
                        switch(params.Profiler.toLowerCase()){
                            case ~/poco/:
                                airTestPocoStage(BuildArgs)
                            break;
                            case ~/uwagot/:
                                airTestUwaStage(BuildArgs)
                            break;
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