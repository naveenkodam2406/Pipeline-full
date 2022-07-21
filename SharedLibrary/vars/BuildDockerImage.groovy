import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    def artifactsResult = null

    pipeline{
        agent{label BuildArgs.AssignedLabel}
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
                        currentBuild.displayName = env.UpstreamJob
                    }
                }
            }
            stage("Fetch Build"){
                steps{
                    script{
                        artifactsResult = cpArtifacts(filter: BuildArgs.EnvironmentParams.Filter, buildNo:env.BuildNo, projectName: BuildArgs.EnvironmentParams.UpstreamJob)
                        currentBuild.displayName += "." + artifactsResult.CopyArtifact_BuildNo
                    }
                }
            }
            stage("Build Image"){
                steps{
                    script{
                        artifactsResult.CopyArtifact_Artifacts.each{artifact ->l:{
                            BuildArgs.TagPrefix.each{_regex, tagPrefix ->j:{
                                if(Utility.RegExFind(artifact, _regex)){
                                    def dockerImageFolder = extract(artifact,env.WORKSPACE)
                                    if(dockerImageFolder != null){
                                        dir(dockerImageFolder){
                                            BuildArgs.DockerConf."${_regex}".each{file, resource ->l:{
                                                writeFile(file: file, text: libraryResource( resource))
                                            }}
                                            BuildArgs.ServerConf."${_regex}".each{copyTo, FileSet->l:{ FileSet.each{file, fileConf -> j:{
                                                def fileName = file.tokenize("\\").last()
                                                def fileContent =  libraryResource( encoding: fileConf.encoding, resource:file)
                                                if(fileConf.Placeholder){
                                                    fileConf.Placeholder.each{k,v ->k:{
                                                        fileContent = fileContent.replace(Utility.RegExFind(fileContent,k),v.replace("{NODE_NAME}", env.NODE_NAME))
                                                    }}
                                                }
                                                writeFile(file: copyTo + "\\" + fileName, text:fileContent, encoding:fileConf.encoding)
                                            }}}}
                                            docker(script: "build . -t ${tagPrefix}")
                                            stage("Push Image"){
                                                docker(script: "push ${tagPrefix}")
                                                
                                                BuildArgs.DockerArchive."${_regex}".each {
                                                    archiveArtifacts artifacts: it, followSymlinks: false, onlyIfSuccessful: true
                                                }
                                                docker(script: "image tag ${tagPrefix} ${tagPrefix}:${artifactsResult.CopyArtifact_BuildNo}")
                                                docker(script: "push ${tagPrefix}:${artifactsResult.CopyArtifact_BuildNo}")
                                                try{
                                                    if(env.ELKURL){
                                                        elasticQuery("addToElastic", """--url ${env.ELKURL} --data "{'opt':'server_dockerimg','job_name':'${env.JOB_NAME}','build_no':'${artifactsResult.CopyArtifact_BuildNo}','build':'${env.JENKINS_URL}${artifactsResult.CopyArtifact_BuildURL}artifact/${artifact}','tag':'${tagPrefix}', 'upstream_url':'${env.JENKINS_URL}${artifactsResult.CopyArtifact_BuildURL}'}" --id server_dockerimg_latest """)
                                                        elasticQuery("addToElastic", """--url ${env.ELKURL} --data "{'opt':'server_dockerimg','job_name':'${env.JOB_NAME}','build_no':'${artifactsResult.CopyArtifact_BuildNo}','build':'${env.JENKINS_URL}${artifactsResult.CopyArtifact_BuildURL}artifact/${artifact}','tag':'${tagPrefix}:${artifactsResult.CopyArtifact_BuildNo}', 'upstream_url':'${env.JENKINS_URL}${artifactsResult.CopyArtifact_BuildURL}'}" --id server_dockerimg_${artifactsResult.CopyArtifact_BuildNo} """)    
                                                    }
                                                }
                                                catch(Exception ex){
                                                    print("Failed when doing addToElastic, but not fail this build.")
                                                    print ex
                                                }
                                            }
                                            try{
                                                docker(script: """rmi -f \$(docker images --format "{{.ID}}" --filter=reference=${tagPrefix})""", shell:true)
                                            }
                                            catch(Exception ex){
                                                print("Failed when doing cleaning up, but not fail this build.")
                                                print ex
                                            }
                                        }
                                    }
                                }
                            }}
                            
                        }}
                        
                    }
                }
            }
            stage("TriggerDownStreamJob"){
                when{expression { params.Deploy == true }}
                steps{
                    script{
                        def paramsList = []
                        paramsList.add("string"(name:"TagNo",value:params.BuildNo))
                        if (BuildArgs.DownstreamJobs){
                            BuildArgs.DownstreamJobs.each{b ->l:{
                                buildName = (b.tokenize(":")[1]) ? (b.tokenize(":")[0]) :   b
                                killQueue = (b.tokenize(":")[1]) ? false                :   true
                                triggerBuild(buildName , paramsList,killQueue)
                            }}
                        }
                    }
                }
            }
        }
        post{
            always {
                script{
                    Utility.UpdateJobStatusOnELK(this)
                    Utility.CleanJobWorkSpace(this)
                    DingSender.SendDingMSG(this,env.DingRobot)
                    EmailSender.SendEmail(this,env.MailTo)
                }
            }
        }
    }
}