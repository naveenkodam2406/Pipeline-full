import lib.*

def call(BuildArgs) {  
    pipeline{
        agent none
        options {
            timestamps()
            timeout(time: 5, unit: 'HOURS')
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "20"))
        }
        stages{
            stage("MakeCheckpointOnForwarding"){
                steps{
                    script{
                        def stdout = ""
                        node(BuildArgs.MasterAgent) {
                            stdout = sh returnStdout: true, script: 'p4 verify -q //...'
                        }
                        if(stdout){
                            print(stdout)
                            error '有异常文件'
                        } 
                        else{
                            node(BuildArgs.ForwardingAgent) {
                                sh('p4 admin checkpoint')
                                sh('p4 admin journal')
                            } 
                        }
                    }
                }
            }
            stage("FWTransFilesToBackupPath"){
                steps{
                    script{
                        node(BuildArgs.ForwardingAgent) {
                            dir(BuildArgs.P4FRServerOnFR){
                                sh("cp -rf "+ BuildArgs.P4FRServerOnFR + "/journals " + BuildArgs.BackupPathOnFR)
                                sh("cp -rf "+ BuildArgs.P4FRServerOnFR + "/archives " + BuildArgs.BackupPathOnFR)
                            }
                        }
                    }
                }
            }
            stage("MasterTransFilesToBackupPath"){
                steps{
                    script{
                        node(BuildArgs.MasterAgent) {
                            dir(BuildArgs.P4MasterServer){
                                sh("rm -rf "+ BuildArgs.P4MasterServer + "/journals/...")
                            }
                        }
                    }
                }
            }
        }
    }
}