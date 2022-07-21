import lib.*
import lib.email.*
import lib.dingding.*
def call(BuildArgs){
    pipeline{
        agent none
        // triggers{
        // gitlab(
        //     triggerOnPush: true, 
        //     triggerOnMergeRequest: true,
        //     branchFilterType: 'All',
        //     secretToken: "")
        // }
        triggers{
        // GenericTrigger(
        //     genericVariables: [
        //         key: 'ref', value: '$.ref'
        //     ],
        //     token: '',

        //     causeString: 'Triggered on $ref',
        //     printContributedVariables: true,
        //     printPostContent: true,
        //                 regexpFilterText: '$ref',
        //                 regexpFilterExpression: 'refs/heads(master|dev)'
        // )
            GenericTrigger(
            genericVariables: [
            [key: 'tag', value: '$.ref',defaultValue:params.Tag]

            ],

            causeString: 'Triggered on $ref',

            token: '',
            tokenCredentialId: '',

            printContributedVariables: true,
            printPostContent: true,

            silentResponse: false,

            regexpFilterText: '$ref',
            regexpFilterExpression: 'refs/heads/' 
            )
  
        }
        
        options{
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
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull != false }}
                steps{
                    script{
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        node(BuildArgs.AssignedLabel){
                            def buildargs = [:] << BuildArgs
                            ws(env.Rootpath){}
                            dir(env.Rootpath){
                                // def tag = "${ref}"
                                if("${tag}"){
                                     
                                buildargs.Branches = [["name":"${tag}"]]
                                }
                               GitPipeline.CheckOutWithConfSetup(this,buildargs, BuildArgs.Submodule)
                            }
                        }
                    }
                }
            }
            stage("UE Build"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            if(params.Build){
                                def  logFile="build_log${env.BUILD_NUMBER}.txt"
                                def  runUAT_path = env.Rootpath + "\\" + env.ProjectPathClient
                                print runUAT_path                             
                                // bat (""" ${runUAT_path} BuildGraph -target="Make Installed Build Win64" -script=Engine/Build/InstalledEngineBuild.xml -set:GameConfigurations=Development -set:WithWin64=true -set:WithWin32=false -set:WithMac=false -set:WithAndroid=false -set:WithIOS=false -set:WithTVOS=false -set:WithLinux=false -set:WithLinuxAArch64=false -set:WithLumin=false -set:WithLuminMac=false -set:WithHoloLens=false -set:WithHTML5=false -set:WithPS4=false -set:WithXboxOne=false -set:WithDDC=false -set:HostPlatformDDCOnly=false -Clean >${logfilePath} """)
                                ueBuildExec(logFile:logFile, logfilepath:env.Rootpath ,runUATPath:runUAT_path)
                                dir(env.CopyBridgepath){
                                    bat(""" ${env.CopyBridge} """)                                
                                }
                                dir(env.FastbuildCopyNewPath){
                                    bat(""" ${env.FastBuildCopy} """)
                                }
                                              
                           }
                           
                        }
                    }
                }

            }
            stage("UE Release"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            print "UE Release"
                            robocopy(env.Rootpath + "\\LocalBuilds", env.SvnPath, " /e /xf *.pdb *.obj ")
                            if(params.SvnCommit){
                                dir(env.SvnPath){
                                    def t = Utility.OS(this).Terminal
                                    cmd = "svn add --force * "
                                    cmdCommit = "svn commit -m '${params.Tag}'"
                                    "${t}"(cmd)
                                    "${t}"(cmdCommit)
                                }
                            }
                        }
                    }
                }

            }
            stage("Wiki Update"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            def t = Utility.OS(this).Terminal
                            cmd = Utility.Git.ResetHardNoneHEAD.CMD
                            cmdpull = Utility.Git.Pull.CMD
                            dir(env.BlackjackPythonPath){
                                "${t}"(cmd)
                                "${t}"(cmdpull)
                            }
                            dir(env.BlackjackUnrealPath){
                                "${t}"(cmd)
                                "${t}"(cmdpull)
                            }
                            dir(env.UnrealWikiPath){
                                "${t}"(cmd)
                                "${t}"(cmdpull)
                            }
                            dir(env.BlackjackPythonPath){
                                bat(""" ${env.GenerateWiki}  """)
                                if(params.WikiCommit){
                                    def desc = params.Tag 
                                    cmdAdd = Utility.Git.Add.CMD
                                    cmdCommit = Utility.Git.Commit.CMD.replace("{DESC}",desc)
                                    cmdPush = Utility.Git.Push.CMD + " -u orign master "
                                    "${t}"(cmdAdd)
                                    "${t}"(cmdCommit)
                                    "${t}"(cmdPush)
                                }
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