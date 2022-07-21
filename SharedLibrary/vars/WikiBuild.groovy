import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs){
    def scmRS = []
    def scmList = []
    pipeline{
       agent none
       options{
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
                    }
                }
            }
            stage("Wiki Pull"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            dir(BuildArgs.Rootpath){
                                ws(BuildArgs.Rootpath){
                                    scmRS.add(GitPipeline.CheckOutWithConfSetup(this, BuildArgs))
                                }
                            }
                            if(scmRS.size() > 0){
                                print scmRS
                                Utility.SetDisplayName(scmRS, currentBuild, scmList, false)
                            }
                        }               
                    }
                }
            }
            stage("Wiki Build"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            dir(BuildArgs.Rootpath){
                                bat(""" ${env.wikibuild}""")
                            }
                        }
                    }
                }
            }
            stage("Wiki Release"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            robocopy(BuildArgs.Rootpath + "\\" + env.ProductionPath, env.OutputPath+ "\\" + "${env.BuildId}" ," /MIR")
                            bat(""" net use  "\\\\192.168.5.57" "bP&/2oY?" /user:Administrator """)
                            robocopy(env.OutputPath+ "\\" + "${env.BuildId}" , "\\\\" + "192.168.5.57" + "\\webapps\\${env.BuildId}", "/MIR")
                            if(env.ELKURL){
                                elasticQuery("addToElastic", """ --url ${env.ELKURL} --data "{'job_name':'${env.BuildId}','build_Path':'${env.RemoteOutputPath}/${env.BuildId}'}" """)
                            }
                        }
                    }
                }

            }
            
        }
        
    }
}