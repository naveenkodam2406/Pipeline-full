import lib.*
def call(projectJson) {
    pipeline{
        agent{label "master"}
        options {
            timestamps()
            timeout(time: 1, unit: 'HOURS')
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "5"))
        }
        stages{
            stage("Clean Host"){
                steps{
                    script{
                        try{
                            sh """#!/bin/bash
                            |find  /var/lib/jenkins/jobs/  -type d -name "libs" -mtime +1 -exec rm -r {} \\;
                            |find  /var/log/jenkins/  -type f -name "*.gz" -exec rm -r {} \\;""".stripMargin()
                        }
                        catch(error){
                            println error
                        }
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