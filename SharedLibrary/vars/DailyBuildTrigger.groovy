import lib.*

def call(BuildArgs) {  
    pipeline{
        agent{label "master"}
        options {
            timestamps()
            skipDefaultCheckout true
            buildDiscarder(logRotator(daysToKeepStr: "7", numToKeepStr: "30"))
        }
        stages{
            stage("Trigger"){
                steps{
                    script{
                        if (BuildArgs.DownstreamJobs){
                            BuildArgs.DownstreamJobs.each{job ->j:{ job.each{ jn, pr ->k:{
                                def paramsList = []
                                pr.each{n,v ->l:{
                                    def pcombination = n.tokenize(".")
                                    paramsList.add("${pcombination[1]}"(name:pcombination[0],value:v))
                                    println ("${pcombination[1]}(name: ${pcombination[0]},value: ${v})")
                                }}
                                triggerBuild(jn , paramsList,false)
                            }}}}
                        }
                    }
                }
            }
        }
    }
}