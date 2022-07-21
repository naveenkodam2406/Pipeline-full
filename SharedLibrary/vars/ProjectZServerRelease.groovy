import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    def scmRS = []
    def scmList = []
    def clientBuildMD5 = ""
    def manifestVersion = [:]
    def nodeList = []
    pipeline{
        agent none
        options {
            timestamps()
            timeout(time: 5, unit: 'HOURS')
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "20"))
        }
        stages{
            stage("EnvSetup"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            Utility.UpdateJobStatusOnELK(this)
                        }
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        env.SCMStr = ""
                        currentBuild.description = "${env.BUILD_NUMBER}_${env.version}"
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull != false }}
                steps{
                    script{
                        node(BuildArgs.AssignedLabel_Server){
                            ws(env.GitRoot){
                                def buildargs = [:] << BuildArgs
                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                bat("""${env.LinkScript} """)
                            }
                            gitLogM1([env.GitRoot],"Server",BuildArgs.Submodule)
                        }
                        if(scmRS.size() > 0){
                            Utility.SetDisplayName(scmRS, currentBuild, scmList,true)
                            def elkscmdata =[:]
                            elkscmdata["SCM"]= scmList
                            env.SCMStr = Utility.ObjToJsonStr(elkscmdata).replace("\"","'")
                            env.SCMStr = env.SCMStr.substring(1,env.SCMStr.length()-1 )+","
                            changeListsFileV2(scmRS,BuildArgs.AssignedLabel)
                        }
                    }
                }
            }
            stage("Excel ConfAnalys"){
                when{expression { params.ExcelConfAnalys != false }}
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            excelConfAnalysis(env.GitRoot)
                            stage("Server Conf"){
                                msbuild(""" ${env.GitRootServer}\\Server\\ProjectZ.ConfigDataLoader\\ProjectZ.ConfigDataLoader.csproj /p:Configuration="Release" /p:Platform="AnyCPU" """)
                                msbuild(""" ${env.GitRootServer}\\Server\\ProjectZ.ConfigDataLoader\\ProjectZ.ConfigDataLoader.csproj /p:Configuration="Debug" /p:Platform="AnyCPU" """)
                            }
                            stage("ClientSync ExcelConf"){
                                unityExec(unityExePath:env.UnityPath, projectPath:env.GitRoot +"\\"+env.ProjectPathClient, 
                                                    executeMethod:env.ExecClientSyncExcelConf,logKeyWords:["GenerateConfigDataAsset complete"],
                                                    last_N_Line:2500, logToFile:false,)
                            }
                            stage("ClientSync CommonCode"){
                                unityExec(unityExePath:env.UnityPath, projectPath:env.GitRoot +"\\"+env.ProjectPathClient, 
                                                    executeMethod:env.ExecClientSyncCommonCode,logKeyWords:["ImportOutSideCommonCode complete"],
                                                    last_N_Line:2500, logToFile:false,)
                            }
                            stage("GitPush AutoGen"){
                                if(params.GitPushAutoGen != false){
                                    if(BuildArgs.GitPushPreBuild){
                                        BuildArgs.GitPushPreBuild.each{k,v ->l:{
                                            if(v.size() == 1 && v[0] =="*.*"){
                                                dir(k){
                                                    //all ready use -A in the cmd
                                                    gitAddFile(".", params.Dryrun)
                                                }
                                            }
                                            else{
                                                def files = []
                                                v.each{ f->j:{ getFilePathByName(k,f).each{
                                                    files.add(it)
                                                }}}
                                                dir(k){
                                                    gitAddFile(files, params.Dryrun)
                                                }
                                            }
                                            
                                        }}
                                        if(scmList.size()>0){
                                            scmList.each{
                                                dir(Utility.OS(this).Path(env.GitRoot + "\\" + it["GIT_CHECKOUT_DIR"])){
                                                    gitCommit("Conf Updated By Jenkins ${env.BUILD_URL}", params.Dryrun)
                                                }
                                            }
                                        }else{
                                            dir(env.GitRoot){
                                                gitCommit("Conf Updated By Jenkins ${env.BUILD_URL}", params.Dryrun)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            stage("Build"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel_Server){
                            print(NODE_NAME)
                            try{
                                def outputRoot = "${env.GitRootServer}\\${env.OutputFolderRoot}\\server"
                                cleanOutput(outputRoot)
                                def serverBuildType = "Debug"
                                stage('Build Server') {
                                    // clean previous build output
                                    if(params.version == "Release"){
                                        serverBuildType = "Release"
                                    }
                                    // clean previous build output
                                    cleanOutput(env.GitRootServer+"\\ServerBin")
                                    dir("${env.GitRootServer}"){
                                        timeout(5){ // adding 5 min timeout to fail the build if the cmd gets stuck
                                            msbuild(solution:env.ServerSln, opt: env."${serverBuildType}.ServerBuildOpt")
                                        }
                                    }
                                    gitStatus([env.GitRoot],"Server", true)
                                }
                                stage('Release Server') {
                                    dir("${env.GitRootServer}"){
                                        bat(env."${serverBuildType}.ServerRelease")
                                    }
                                    
                                    def ver = Utility.ObjToJsonStr([ServerVersion:env.BuildId],true)
                                    writeFile(file: env.GitRootServer+"\\ServerBin\\version.txt", text:ver)
                                    mdFive(path:env.GitRootServer+"\\ServerBin")
                                    robocopy(env.GitRootServer+"\\ServerBin","${outputRoot}\\Server","/MIR")
                                    packServer(env.GitRootServer+"\\ServerBin", null, BuildArgs.ServerConf)
                                }
                                stage('Upload Server') {
                                    if(params.UploadServer){
                                        try{
                                            def zipName = "${outputRoot}\\Server.zip"
                                            compress(zip:zipName, path:"${outputRoot}\\Server", mode:"1",relpath:"..")
                                            print ("Upload Server ${outputRoot}\\Server.zip")
                                            if(BuildArgs.ServerUploadFTP){
                                                uploadServerV2(path:outputRoot, file: "Server.zip", serverConfPath:BuildArgs.ServerUploadFTP, 
                                                serverTag:env.UpdateServerTag, port:12099, opt:"-pasv 1" )
                                            }
                                        }
                                        catch(Exception ex){
                                            // set unstable not fail the build
                                            unstable(message: "Upload to FTP is unstable")
                                            print ex
                                        }
                                    }
                                    else{
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            }
                            finally{
                                Utility.CleanParallelJobWorkSpace(this)
                            }
                        }
                    }
                }
            }
        }
        post{
            always {
                script{
                    node(BuildArgs.AssignedLabel){
                        Utility.UpdateJobStatusOnELK(this)
                        Utility.AddJobResultOnELK(this)
                        Utility.CleanJobWorkSpace(this)
                        DingSender.SendDingMSG(this,env.DingRobot)
                        try{
                            EmailSender.SendEmail(this,env.MailTo,scmRS)
                        }
                        catch(Exception ex){
                            EmailSender.SendEmail(this,env.MailTo)
                        }
                    }
                    
                }
            }
        }
    }
}