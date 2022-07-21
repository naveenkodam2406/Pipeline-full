import lib.*
import lib.email.*

def call(BuildArgs) {
    def scm
    pipeline{
        agent{label "master"}
        options{
            timestamps()
            skipDefaultCheckout true
            buildDiscarder(logRotator(daysToKeepStr: "7", numToKeepStr: "30"))
        }
        stages{
            stage("EnvSetup"){
                steps{
                    script{
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull != false }}
                steps{
                    script{
                        scm = GitPipeline.CheckOutWithConfSetup(this, BuildArgs, BuildArgs.Submodule )
                    }
                }
            }
            stage("Kick Builds"){
                steps{
                    script{
                        // removing all check since the git exclusive user rule works lol.
                        def paramsList = []
                        if (BuildArgs.DownstreamJobs){
                            BuildArgs.DownstreamJobs.each{b ->l:{
                                buildName = (b.tokenize(":")[1]) ? (b.tokenize(":")[0]) :   b
                                killQueue = (b.tokenize(":")[1]) ? false                :   true
                                triggerBuild(buildName , paramsList,killQueue)
                            }}
                        }
                        if(params.DownstreamJobs){
                            params.DownstreamJobs.tokenize(",").each{b ->l:{
                                buildName = (b.tokenize(":")[1]) ? (b.tokenize(":")[0]) :   b
                                killQueue = (b.tokenize(":")[1]) ? false                :   true
                                triggerBuild(buildName , paramsList,killQueue)
                            }}
                        }
                    }
                }
            }
        }
        post{
            failure {
                script{
                    
                    EmailSender.SendEmail(this,BuildArgs.MailTo)
                }
            }
        }
    }
}