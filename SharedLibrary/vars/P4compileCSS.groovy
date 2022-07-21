import lib.*
import lib.email.*
import lib.dingding.*

def http_Request(messages,status){
    def body = groovy.json.JsonOutput.toJson([
        "messages":[messages],
        "url":"http://192.168.7.120:8080/job/BE/" + "${env.BUILD_NUMBER}" + "/console",
        "status":status
    ])
    println body
    response = httpRequest consoleLogResponseBody: true, contentType: 'APPLICATION_JSON', httpMode: 'POST', requestBody: body, url: "${env.update_0}", validResponseCodes: '200'
    }

def messages = "P4unshelve"
def status = "pass"
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
                // stage("CIprocess"){
                //     steps{
                //         script{
                //             def args = [
                //                 "Changelog": true,
                //                 "Poll": true,
                //                 "WorkspaceClass": "StreamWorkspaceImpl",
                //                 "WorkspaceNameFormat": "{NODE_NAME}-P4Example",
                //                 "AndroidRoot": "e:\\P4Example",
                //                 "Credential": "5.108.Test.P4",
                //                 "Populate": "SyncOnlyImpl",
                //                 "ClientSpec": """//streamsDepot/framework""",
                //             ]
                //             ws(args.AndroidRoot){}
                //             def scmEnv = null
                //             dir(args.AndroidRoot){
                //                 scmEnv = P4Pipeline.Checkout(this, args)
                //             }
                //         }
                //     }
                // } 
                stage("P4sync"){
                    steps{
                        script{
                            try{
                                dir("${params.Workspace_path}"){
                                p4sync charset: 'none', credential: 'a2bf4a66-7d1e-46e1-89db-ab138f57e386', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', populate: forceClean(have: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true), source: streamSource('//streamsDepot/framework')
                                }
                                messages = "P4sync suc"
                                status = "running"
                            }
                            catch(Exception a){
                                messages = "P4sync fail"
                                status = "running"
                            }
                            finally{
                                http_Request(messages,status)
                                }
                        }
                    }
                }
                stage("P4unshelve"){
                    steps{
                        script{
                            try{
                                dir("${params.Workspace_path}"){
                                p4unshelve credential: 'a2bf4a66-7d1e-46e1-89db-ab138f57e386', ignoreEmpty: false, resolve: 'ay', shelf: "${env.change_0}", tidy: false, workspace: streamSpec(charset: 'none', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', pinHost: false, streamName: '//streamsDepot/framework')
                                }
                                messages = "P4unshelve suc"
                                status = "pass"
                            }
                            catch(Exception a){
                                messages = "P4unshelve fail"
                                status = "fail"
                            }
                            finally{
                                http_Request(messages,status)
                               }
                        }
                    }
                }
                stage("P4clean"){
                    steps{
                        script{
                            try{
                                dir("${params.Workspace_path}"){
                                p4sync charset: 'none', credential: 'a2bf4a66-7d1e-46e1-89db-ab138f57e386', format: 'jenkins-${NODE_NAME}-${JOB_NAME}', populate: forceClean(have: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: '', quiet: true), source: streamSource('//streamsDepot/framework')
                                }
                                messages = "P4clean suc"
                                status = "pass"
                            }
                            catch(Exception a){
                                messages = "P4clean fail"
                                status = "fail"
                            }
                            finally{
                                http_Request(messages,status)
                                }
                        }
                    }
                }
            }
        }
}