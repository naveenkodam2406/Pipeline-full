import lib.*
def call(BuildArgs){
    pipeline{ 
           agent {label "${params.agenthost}"}               
            stages{      
                stage("git clone"){
                    steps{
                        script{
                           dir("C:\\Users\\zengjianxiong\\Desktop\\JenkinsStatusTool\\"){
                               GitPipeline.CheckOutWithConfSetup(this, BuildArgs, false)
                                //bat("""git clone http://zengjianxiong:BEzengjianxiong@192.168.5.23:8090/BuildEngineer/Git/GitExtensions.git""")
                           }
                           
                        }
                    }
                } 
                stage("build"){
                    steps{
                        script{
                           msbuild(
                            ["msbuild":""" "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\MSBuild\\Current\\Bin\\MSBuild.exe" """,
                            "solution":"C:\\Users\\zengjianxiong\\Desktop\\JenkinsStatusTool\\StatusAndUpdateApp.sln",
                            "opt":""" /p:Configuration=Release /p:Platform="Any CPU" """]
                            )
                        }
                    }
                } 
                stage("package StatusTool"){
                    steps {
                        script{
                            bat(""" "C:\\Program Files\\7-Zip\\7z.exe" a C:\\Users\\zengjianxiong\\Desktop\\StatusTool.zip C:\\Users\\zengjianxiong\\Desktop\\JenkinsStatusTool\\StatusToolApp\\bin\\Release""")
                        }
                    }
                }
                stage("package AutoUpdateApp"){
                    steps {
                        script{
                            bat(""" "C:\\Program Files\\7-Zip\\7z.exe" a C:\\Users\\zengjianxiong\\Desktop\\StatusTool.zip C:\\Users\\zengjianxiong\\Desktop\\JenkinsStatusTool\\AutoUpdateApp\\bin\\Release""")                          
                        }
                    }
                }
                // stage("copy"){
                //     steps {
                //         script{
                //             bat("""net use \\192.168.1.7""")
                //             bat("""xcopy /s /y C:\\apache-tomcat-9.0.45\\webapps\\ROOT\\files\\GitExtensions.zip "\\192.168.1.7\\资料共享\\配置管理\\Python软件\\" """)
                //         }
                //     }
                // }
            }
        }
}