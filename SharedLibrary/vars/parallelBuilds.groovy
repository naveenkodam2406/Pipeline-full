def call(buildNameList, paramList, failPipeline = true){ 
    //by default, if the build in parallel fails, the whole pipeline will fail 
    def transformIntoStep = { i, j, k ->l:{return {
            build job: i, parameters: j, propagate: k
        }}  
    }
    def stepsForParallel = [:]
    for (int i = 0; i < buildNameList.size(); i++){
        paramTemp = paramList.collect()
        paramTemp.add(booleanParam(name: "${i}", value: true) )//to fake a parameter to trigger same job multipule times
        stepsForParallel["${i} ${buildNameList[i]}"] = transformIntoStep(buildNameList[i], paramTemp, failPipeline)
    }
    parallel stepsForParallel
}