import lib.*
import lib.email.*
import lib.dingding.*
def call(Args){
    pipeline{
            agent{label "${params.slaveIP}"}
            options {
                timestamps()
                timeout(time: 1, unit: 'HOURS')
                skipDefaultCheckout true
                buildDiscarder(logRotator(numToKeepStr: "20"))
            }
            stages{
                stage("updata"){
                    steps{
                        script{
                            Args.each{  Args_item ->
                                stage(Args_item.key){
                                    node(Args_item.key){
                                        bat ("set JENKINS_NODE_COOKIE=dontKillMe && pushd "+Args_item.value+"&&process_updata.bat")
                                        println (Args_item.key+" update completed in "+Args_item.value)
                                    }
                                }
                            }
                        }
                    }
                } 
                stage("clean"){
                    steps {
                        script{
                            Utility.CleanJobWorkSpace(this)
                        }
                    }
                }
            }
        }
}