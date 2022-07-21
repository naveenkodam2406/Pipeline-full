import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    def artifactsResult = null
    def dockerDeploy = false
    pipeline{
        agent{label params.AssignedLabel}
        options {
            timestamps()
            timeout(time: 1, unit: 'HOURS')
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "20"))
        }
        stages{
            stage("EnvSetup"){
                steps{
                    script{
                        // Utility.UpdateJobStatusOnELK(this)
                        currentBuild.description = env.NODE_NAME
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                    }
                }
            }
            stage("HelloWorld"){
                steps{
                    script{
                        print params.Test
                        print env.TestInEnv
                        
                    }
                }
            }
        }
        post{
            always {
                script{
                    // Utility.UpdateJobStatusOnELK(this)
                    DingSender.SendDingMSG(this,env.DingRobot)
                    Utility.CleanJobWorkSpace(this)
                }
            }
        }
    }
}