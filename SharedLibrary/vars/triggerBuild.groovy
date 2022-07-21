import lib.Utility

import hudson.model.*


@NonCPS
def TestingQueue(jobName, retryTime){
    if (retryTime > 0){
        Jenkins.instance.queue.items.each {
            if(it.task.name.toLowerCase().contains(jobName.toLowerCase())){
                sleep(3)
                retryTime --
                TestingQueue(jobName, retryTime)
            }
            retryTime = 0
        }
    }
    Jenkins.instance.queue.items.each {
        if(it.task.name.toLowerCase().contains(jobName.toLowerCase())){
            if (!it.isBuildable()){
                Jenkins.instance.queue.cancel(it.task)
            }
        }
    }
}
// def paramList = []
// e.g.paramList.add("string"(name:Changelist,value:${P4_CHANGELIST}))
def call(jobName, paramList,killQueue=true){
    if(killQueue){
        TestingQueue(jobName, 3)
    }
    build(job:jobName, parameters: paramList, wait: false, propagate: false)
    // try{
        
    // }
    // catch(Exception ex){
    //     println ex
    // }
}