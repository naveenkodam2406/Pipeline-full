import lib.*
def call(BuildArgs) {
    pipeline{
        agent{label BuildArgs.AssignedLabel}
        options {
            skipDefaultCheckout true
            timestamps()
            timeout(time: 5, unit: 'HOURS')
            buildDiscarder(logRotator(numToKeepStr: "7"))
        }
        stages{
            stage("Test"){
                steps{
                    script{
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        P4Pipeline.Checkout(this, BuildArgs)
                        p4Precache(env.P4_PORT,env.P4_USER,env.P4_CLIENT)
                    }
                }
            }
        }
        post{
            always {
                script{
                    Utility.CleanJobWorkSpace(this)
                }
            }
        }
    }
}