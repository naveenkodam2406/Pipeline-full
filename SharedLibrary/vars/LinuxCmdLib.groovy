import lib.*
import lib.email.*

def call() {
    pipeline{
        agent{label params.AssignedLabel}
        options {
            timestamps()
            skipDefaultCheckout true
            buildDiscarder(logRotator(daysToKeepStr: "7", numToKeepStr: "30"))
        }
        stages{
            stage("Exec"){
                steps{
                    script{
                        // params overwrite buildArgs.EnvironmentParams
                        currentBuild.description = "VM: ${env.NODE_NAME}"
                        currentBuild.displayName +=".${env.NODE_NAME}" 
                        params.Command.tokenize("\r\n").each{ cmd -> l:{
                            echo(cmd.trim())
                            try{
                                sh(""" ${cmd.trim()} """)
                            }
                            catch(Exception e) {
                                print e // print error not fail the build, coz sometimes the permission issue is an error which I do not care.
                            }
                        }}
                    }
                }
            }
        }
        post{
            unsuccessful{
                script{
                    EmailSender.SendEmail(this,env.MailTo)
                }    
            }
            cleanup{
                script{
                    Utility.CleanJobWorkSpace(this)
                }
            }
        }
    }
}