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
                        }
                        currentBuild.description += "_${params.Profiler}"
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull == true }}
                steps{
                    script{
                        switch(params.Profiler.toLowerCase()){
                            case ~/poco/:
                                node(BuildArgs.AssignedLabel_Android){
                                    ws(env.GitRootPoco){}
                                    dir(env.GitRootPoco){
                                        def buildargs = [:] << BuildArgs
                                        scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                        projectULinkFramework(env.GitRootPoco+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootPoco+"\\"+BuildArgs.CheckOutFolder.Framework)
                                        dir(env.LinkScriptWS){
                                            bat("""${env.LinkScript} """)
                                        }
                                    }
                                    gitLogM1([env.GitRootPoco+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootPoco+"\\"+BuildArgs.CheckOutFolder.Framework],"poco",BuildArgs.Submodule)
                                }
                            break;
                            case ~/uwagot/:
                                node(BuildArgs.AssignedLabel_Android){
                                    ws(env.GitRootPoco){}
                                    dir(env.GitRootUwa){
                                        def buildargs = [:] << BuildArgs
                                        scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                        projectULinkFramework(env.GitRootUwa+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootUwa+"\\"+BuildArgs.CheckOutFolder.Framework)
                                        dir(env.LinkScriptWS){
                                            bat("""${env.LinkScript} """)
                                        }
                                    }
                                    gitLogM1([env.GitRootUwa+"\\"+BuildArgs.CheckOutFolder.Client, env.GitRootUwa+"\\"+BuildArgs.CheckOutFolder.Framework],"uwagot",BuildArgs.Submodule)
                                }
                            break;
                            case ~/mipmap/:
                                node(BuildArgs.AssignedLabel_Android){
                                    ws(env.GitRootPoco){}
                                    dir(env.GitRootMipmap){
                                        def buildargs = [:] << BuildArgs
                                        scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                        projectULinkFramework(env.GitRootMipmap+"\\"+BuildArgs.CheckOutFolder.Client,env.GitRootMipmap+"\\"+BuildArgs.CheckOutFolder.Framework)
                                        dir(env.LinkScriptWS){
                                            bat("""${env.LinkScript} """)
                                        }
                                    }
                                    gitLogM1([env.GitRootMipmap+"\\"+BuildArgs.CheckOutFolder.Client, env.GitRootMipmap+"\\"+BuildArgs.CheckOutFolder.Framework],"mipmap",BuildArgs.Submodule)
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
                            case ~/mipmap/:
                                env.Options +=" mipmap#1"
                                airTestMipmapStage(BuildArgs)
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