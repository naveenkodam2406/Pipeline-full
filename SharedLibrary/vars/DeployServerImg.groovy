import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    def artifactsResult = null
    def dockerDeploy = false
    pipeline{
        agent{label params.AssignedLabel}
        options {
            timestamps()
            timeout(time: 1, unit: 'HOURS')
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "20"))
        }
        stages{
            stage("EnvSetup"){
                steps{
                    script{
                        Utility.UpdateJobStatusOnELK(this)
                        currentBuild.description = env.NODE_NAME
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                    }
                }
            }
            stage("BackUpDocker"){
                steps{
                    script{
                        docker(script: """stop \$(docker ps --format "{{.ID}}" --all --filter ancestor=\$(docker images --format "{{.ID}}" --filter reference=${BuildArgs.ServerConf.TagPrefix}))""" ,shell:true, propagate:false)
                        try{
                            if(testPath(env.DockerFolder)){
                                dir(env.DockerFolder){
                                    compress(env.DockerFolder+"-"+Utility.GetCurrentDate()+".zip",env.DockerFolder)
                                    deleteDir()
                                }
                            }
                        }
                        catch(Exception ex){
                            print "compress and delete ${env.DockerFolder} are failed"
                            print ex
                        }
                        
                    }
                }
            }
            stage("Clean"){
                steps{
                    script{
                        bat (script:"taskkill /f /im Center.exe", returnStatus:true)
                        bat (script:"taskkill /f /im GameServer03.exe", returnStatus:true)
                        bat (script:"taskkill /f /im GameServer02.exe", returnStatus:true)
                        bat (script:"taskkill /f /im DispatchServer.exe", returnStatus:true)
                        bat (script:"taskkill /f /im GMServer.exe", returnStatus:true)
                        bat (script:"taskkill /f /im GMTServer.exe", returnStatus:true)
                        bat (script:"taskkill /f /im GMT.exe", returnStatus:true)
                        print ("""container rm -f \$(docker ps --format "{{.ID}}" --all --filter ancestor=\$(docker images --format "{{.ID}}" --filter reference=${BuildArgs.ServerConf.TagPrefix}))""")
                        docker(script: """container rm \$(docker ps --format "{{.ID}}" --all --filter ancestor=\$(docker images --format "{{.ID}}" --filter reference=${BuildArgs.ServerConf.TagPrefix}))""" ,shell:true, propagate:false)
                        print ("""image rm  -f \$(docker images --format "{{.ID}}" --filter=reference=${BuildArgs.ServerConf.TagPrefix})""")
                        docker(script: """image rm  -f \$(docker images --format "{{.ID}}" --filter=reference=${BuildArgs.ServerConf.TagPrefix})""", shell:true, propagate:false)
                        
                    }
                }
            }
            stage("Deploy DockerImg"){
                when{expression { params.DeployDockerImg == true }}
                steps{
                    script{
                        dir(env.DockerFolder){
                            BuildArgs.ServerConf.DockerPlaceholder.each{folder, files->l:{files.each{file, conf->j:{
                                def content = libraryResource( encoding: conf.encoding, resource:file)
                                conf.Placeholder.each{k,v ->k:{
                                    content = content.replace(Utility.RegExFind(content,k),v.replace("{NODE_NAME}", env.NODE_NAME))
                                }}
                                def fileName = file.tokenize("\\").last()
                                writeFile(file: folder + "\\" + fileName, text: content, encoding:conf.encoding)
                            }}}}
                            env.dockerStartUpFile = BuildArgs.ServerConf.DockerStartUpFile.tokenize("\\").last()
                            env.startUpScript = libraryResource(resource:BuildArgs.ServerConf.DockerStartUpFile)
                            def buildSuffix = "latest" 
                            def refTag = BuildArgs.ServerConf.TagPrefix
                            if(params.TagNo){
                                startUpScript = startUpScript.replace("%1",":"+params.TagNo)
                                buildSuffix = params.TagNo
                                refTag = ":" + params.TagNo
                            }
                            currentBuild.displayName += "." + buildSuffix
                            writeFile(file: env.DockerFolder + "\\" + dockerStartUpFile, text: startUpScript)
                            def t = Utility.OS(this).Terminal
                            try{
                                "${t}"(env.DockerFolder + "\\" + dockerStartUpFile)
                                dockerDeploy = true
                                try{
                                    if(env.ELKURL){
                                        elasticQuery("addToElastic", """--url ${env.ELKURL} --data "{'opt':'server_deploy','job_name':'${env.JOB_NAME}','type':'docker','host':'${env.NODE_NAME}','is_windows':'${!isUnix()}','build':'${refTag}','build_no':'${buildSuffix}', 'upstream_url':''}" """)
                                    }
                                }
                                catch(Exception ex){
                                    print("Failed when doing addToElastic, but not fail this build.")
                                    print ex
                                }
                            }
                            catch(Exception ex){
                                print ex
                                unstable(message: "Fallback to normal Deploy")
                            }
                        }
                    }
                }
            }
            stage("Deploy from Zip"){
                when{expression { params.DeployZip == true || dockerDeploy == false }}
                steps{
                    script{
                        // to throw away the returncode.
                        artifactsResult = cpArtifacts(filter: BuildArgs.ServerConf.Filter, buildNo:params.TagNo, projectName: env.UpstreamJob)
                        currentBuild.displayName += "."+ env.UpstreamJob +"."+ artifactsResult.CopyArtifact_BuildNo
                        artifactsResult.CopyArtifact_Artifacts.each{artifact ->l:{
                            if(Utility.RegExFind(artifact, BuildArgs.ServerConf.Filter[0])){
                                def ws = extract(artifact, env.ZipFolder)
                                BuildArgs.ServerConf.ZipPlaceholder.each{copyTo, FileSet->l:{ FileSet.each{file, fileConf -> j:{
                                    def fileName = file.tokenize("\\").last()
                                    def fileContent =  libraryResource( encoding: fileConf.encoding, resource:file)
                                    if(fileConf.Placeholder){
                                        fileConf.Placeholder.each{k,v ->k:{
                                            fileContent = fileContent.replace(Utility.RegExFind(fileContent,k),v.replace("{NODE_NAME}", env.NODE_NAME))
                                        }}
                                    }
                                    writeFile(file: Utility.OS(this).Path(ws+"\\"+copyTo + "\\" + fileName), text:fileContent, encoding:fileConf.encoding)
                                }}}}
                                def runtimeFolder = env.ZipFolder+"\\server"
                                def tmp = Utility.OS(this).Path(runtimeFolder+"\\"+BuildArgs.ServerConf.ZipStopAll.Folder)
                                try{
                                    bat(script: "pushd ${tmp} && set JENKINS_NODE_COOKIE=dontKillMe && " + BuildArgs.ServerConf.ZipStopAll.Script,  returnStdout: true)
                                }
                                catch(Exception ex){
                                    print("Failed when doing ${tmp}, but not fail this build.")
                                    print ex
                                }
                                robocopy(ws, runtimeFolder,"/E /S /MIR")
                                tmp = Utility.OS(this).Path(runtimeFolder+"\\"+BuildArgs.ServerConf.ZipStartUpFile.Folder)
                                def startUpFile = psGetFileNameByType(tmp ,BuildArgs.ServerConf.ZipStartUpFile.Script)[0] // always the first one
                                print bat(script:  "pushd ${tmp} && set JENKINS_NODE_COOKIE=dontKillMe && " + startUpFile, returnStdout: true)
                                try{
                                    if(env.ELKURL){
                                        elasticQuery("addToElastic", """--url ${env.ELKURL} --data "{'opt':'server_deploy','job_name':'${env.JOB_NAME}','type':'zip','host':'${env.NODE_NAME}','is_windows':'${!isUnix()}','build':'${env.JENKINS_URL}${artifactsResult.CopyArtifact_BuildURL}artifact/${artifact}','build_no':'${artifactsResult.CopyArtifact_BuildNo}', 'upstream_url':'${env.JENKINS_URL}${artifactsResult.CopyArtifact_BuildURL}'}" """)
                                    }
                                }
                                catch(Exception ex){
                                    print("Failed when doing addToElastic, but not fail this build.")
                                    print ex
                                }
                            }
                        }}
                    }
                }
            }
        }
        post{
            always {
                script{
                    Utility.UpdateJobStatusOnELK(this)
                    DingSender.SendDingMSG(this,env.DingRobot)
                    Utility.CleanJobWorkSpace(this)
                }
            }
        }
    }
}