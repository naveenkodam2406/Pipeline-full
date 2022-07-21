import lib.*
import lib.email.*

def call(buildArgs) {
    pipeline{
        agent{label "master"} // scheduler always running on master
        options {
            timestamps()
            skipDefaultCheckout true
            buildDiscarder(logRotator(daysToKeepStr: "7", numToKeepStr: "30"))
        }
        stages{
            stage("Trigger"){
                steps{
                    script{
                        buildArgs.Nodes.each{ node -> l:{
                            stage(node){
                                def paramsList = []
                                paramsList.add("string"(name:"AssignedLabel",value:node))
                                paramsList.add("string"(name:"Command",value:buildArgs.Command))
                                if (buildArgs.DownstreamJobs){
                                    buildArgs.DownstreamJobs.each{b ->l:{
                                        triggerBuild(b , paramsList, false)
                                    }}
                                }
                            }
                        }}
                        
                    }
                }
            }
        }
        post{
            cleanup{
                script{
                    Utility.CleanJobWorkSpace(this)
                }
            }
        }
    }
}