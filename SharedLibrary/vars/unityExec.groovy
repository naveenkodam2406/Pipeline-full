import lib.Utility


def call(Map kwargs)
{
    if(kwargs.failedBuildIfNotFound == null) kwargs.failedBuildIfNotFound=true
    if(kwargs.logKeyWords == null) kwargs.logKeyWords=[]
    if(kwargs.options == null) kwargs.options= ""
    if(kwargs.apk == null) kwargs.apk = ""
    if(kwargs.output == null) kwargs.output = ""
    def outputApk = Utility.OS(this).Path("${kwargs.output}")
    def notFound = true
    def t = Utility.OS(this).Terminal
    try{
        def ts = Utility.GetCurrentDate("yyyy.MM.dd_HH.mm.ss")
        def tmpMethod = env.justBuildAB == "true" ? kwargs.buildAssetBundle.tokenize(".")[1].trim() : kwargs.executeMethod.tokenize(".")[1].trim()
        def prefix = STAGE_NAME.replace(" ","")
        if(kwargs.logToFile == false || kwargs.logToFile == null){
            kwargs.logFile = "-"
        }else{
            kwargs.logFile = "${prefix}.${tmpMethod}.${ts}.log"
        }
        if(kwargs.apk != ""){
            if(outputApk != ""){
                outputApk = Utility.OS(this).Path(""" "${outputApk}\\${kwargs.apk}" """)
            }else{
                outputApk = kwargs.apk
            }
        } 
        kwargs.unityExePath = Utility.OS(this).Path(kwargs.unityExePath)
        kwargs.projectPath =  Utility.OS(this).Path(kwargs.projectPath)
        def buildScript =  """ "${kwargs.unityExePath}" ${outputApk}  -quit -batchmode -projectPath "${kwargs.projectPath}" -logFile ${kwargs.logFile} -executeMethod ${kwargs.executeMethod}  ${kwargs.options}  """
        // use env var as the switch, so we don't need to pass any extra kwargs for this 
        if (env.justBuildAB == "true"){
            kwargs.checkOutput = null;
            buildScript =  """ "${kwargs.unityExePath}"  -quit -batchmode -projectPath "${kwargs.projectPath}" -logFile ${kwargs.logFile} -executeMethod ${kwargs.buildAssetBundle} ${kwargs.options}"""
        }
        try{
            writeFile file: "${prefix}.${tmpMethod}.${ts}.params.txt", text: buildScript, encoding:"UTF-8"
            archiveArtifacts artifacts: "${prefix}.${tmpMethod}.${ts}.params.txt", followSymlinks: false, allowEmptyArchive : true
        }
        catch(Exception ex){
            print("Archiving Unity Build params failed")
        }
        if(kwargs.output){
            dir(kwargs.output){
                "${t}"(script:buildScript)
            }
        }else{
            "${t}"(script:buildScript)
        }
        
    }
    catch(Exception ex){
        print ex
    }
    finally {
        def line = []
        if(kwargs.logFile != "-"){
            def rawContent = null
            if(kwargs.output){
                dir(kwargs.output){
                    archiveArtifacts artifacts: kwargs.logFile, followSymlinks: false, allowEmptyArchive : true
                    printLogFromFile(kwargs.logFile)
                }
            }else{
                archiveArtifacts artifacts: kwargs.logFile, followSymlinks: false, allowEmptyArchive : true
                printLogFromFile(kwargs.logFile)
            }
        }
        line = logScan(kwargs.logKeyWords, kwargs.last_N_Line)
        if(line.size() == 0){
            if(kwargs.failedBuildIfNotFound){
                print ("Failed to find:  "+ kwargs.logKeyWords)
                error ("Failed to find:  "+ kwargs.logKeyWords)
            }
        }
        if(line.size() > 0 ){
            line.each{
                if(it.contains("error, Check log Path:")){
                    def logpath = it.split("error, Check log Path:")[1].trim()
                    printLogFromFile(Utility.OS(this).Path(kwargs.projectPath + logpath.substring(1,logpath.size())))
                }
            }
            if(!kwargs.failedBuildIfNotFound){
                error (line.join("\n"))
            }
        }
    }
    if(kwargs.checkOutput != null){
        // to do to make pacakage check inside unity run 
        if(psGetFileNameByType(kwargs.output,kwargs.checkOutput).size() == 0) {
            error( "No ${kwargs.checkOutput} found, build failed")
        }
    }
}