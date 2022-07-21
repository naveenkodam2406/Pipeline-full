import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    pipeline {
        agent{label BuildArgs.AssignedLabel}
        options{
            timestamps()
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "30"))
        }
        stages {
            stage('Transmission') {
                steps {
                    script{
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        currentBuild.displayName = env.BUILD_NUMBER + "." + env.BuildFolder
                        parallel(
                            "FTP":{
                                stage("FTP"){
                                    if(params.FTP == true){
                                        def clientRoot = psFindFolderByName(BuildArgs.RootFolder + "\\" + params.BuildFolder ,BuildArgs.ClientFolderFilter,BuildArgs.ClientDepth)
                                        print clientRoot
                                        if (clientRoot == "" || clientRoot == null){
                                            unstable(message: "Failed to find folder, skipping FTP")
                                            return
                                        }
                                        def ftpAccount = BuildArgs.FTPAccount?BuildArgs.FTPAccount:BuildArgs.FTPServer
                                        withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ftpAccount), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                            pwd = pwd.replace("%","%%")
                                            ftpUpload(host:BuildArgs.FTPServer, "uid":uid, "pwd":pwd, remote:"", path:clientRoot, port:BuildArgs.FTPPort,opt:"-pasv 1 -ipv6 -enc UTF-8")
                                        }
                                    }
                                    else{
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                    
                                }
                            },
                            "RSYNC":{
                                stage("RSYNC"){
                                    if(params.RSYNC == true){
                                        params.BuildFolder.split("_").each{
                                            def tmp = Utility.RegExFind(it,/^\d+$/)
                                            if(tmp != null){
                                                env.BuildNumber = tmp
                                            }
                                        }
                                        def root = psFindFolderByName(BuildArgs.RootFolder + "\\"+ params.BuildFolder ,"*_"+ env.BuildNumber,"-Depth 1")
                                        print root
                                        if (root == "" || root == null) {
                                            unstable(message: "Failed to find folder, skipping RSYNC")
                                            return
                                        }
                                        ws(BuildArgs.UploadWS){
                                            powershell(".\\"+BuildArgs.UploadScript + " -BuildPath " + root)
                                        }
                                        
                                    }
                                    else{
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                    
                                }
                            },
                            "Notify":{
                                stage("Notify"){
                                    DingSender.SendDingMSG(this,env.DingRobot,"STARTED")
                                }
                            }
                        )
                    } 
                }
            }
        }
        post{
            always{
                script{
                    Utility.CleanJobWorkSpace(this)
                    DingSender.SendDingMSG(this,env.DingRobot)
                    EmailSender.SendEmail(this,env.MailTo)
                }
            }
        }
    }

}