import lib.*
import lib.email.*
import lib.dingding.*


def call(BuildArgs){
    pipeline{
       agent none
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
            stage("check Wiki Service"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            def osType = Utility.OS(this)
                            def t = osType.Shell
                            def sericeCode = "${t}"(script:""" Get-Service Confluence040719174729 | Select -First 1 -ExpandProperty Status | Out-String """,returnStdout: true)
                            timeout(10){
                                print sericeCode
                                if(sericeCode.contains("Running")){
                                    bat(""" net stop Confluence040719174729 """)
                                }
                                while(!sericeCode.contains("Stopped")){
                                    sericeCode = "${t}"(script:""" Get-Service Confluence040719174729 | Select -First 1 -ExpandProperty Status | Out-String """,returnStdout: true)
                                    sleep(10)
                                    if(sericeCode.contains("Running")){
                                        bat(""" net stop Confluence040719174729 """)
                                    }
                                }
                            }   
                        } 
                    }
                }
            }
            stage("Wiki Deploy"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            def rootPath = params.VersionSelect
                            def rootPathwin = rootPath.replace("/","\\")
                            // cleanFolder("\"" + env.DeployPath  + "\\" + "confluence(1)" + "\"",0)
                            bat(""" rd /s/q "${env.DeployPath}\\confluence(1)" """)
                            //创建一个副本
                            robocopy( "\"" +env.DeployPath +  "\\" + "confluence" + "\"", "\"" + env.DeployPath + "\\" + "confluence(1)" + "\"", "/MIR")
                            //部署,替换文件
                            robocopy( rootPathwin + "\\" + "confluence", "\"" + env.DeployPath + "\\" + "confluence" + "\"","/MIR")
                            //替换文件
                            robocopy("\"" + env.DeployPath + "\\confluence(1)\\WEB-INF\\classes" + "\"", "\"" + env.DeployPath  + "\\confluence\\WEB-INF\\classes" +"\"", "confluence-init.properties /MIR")
                            robocopy("\"" + env.DeployPath + "\\confluence(1)\\WEB-INF\\lib" + "\"", "\"" + env.DeployPath + "\\confluence\\WEB-INF\\lib" + "\"", "atlassian-extras-decoder-v2-3.4.1.jar /MIR")
                            robocopy("\"" + env.DeployPath + "\\confluence(1)\\WEB-INF\\lib" + "\"", "\"" + env.DeployPath + "\\confluence\\WEB-INF\\lib" + "\"", "mysql-connector-java-5.1.25-bin.jar /MIR")
                        }
                    }
                }
            }
            stage("Wiki start"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            bat(""" net start Confluence040719174729 """)
                        }
                    }
                }
            }
            stage("Check Service"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            def osType = Utility.OS(this)
                            def t = osType.Shell
                            def sericeCode = "${t}"(script:""" Get-Service Confluence040719174729 | Select -First 1 -ExpandProperty Status | Out-String """,returnStdout: true)
                            timeout(5){
                                while(!sericeCode.contains("Running")){
                                    sericeCode = "${t}"(script:""" Get-Service Confluence040719174729 | Select -First 1 -ExpandProperty Status | Out-String """,returnStdout: true)
                                    sleep(10)
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
                    } 
                }
            }
        }
    }
}