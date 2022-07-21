import lib.*
def call(BuildArgs){
    pipeline{ 
           agent {label "${params.agenthost}"}   
            options{
            skipDefaultCheckout true
        }            
            stages{      
                stage("git clone"){
                    steps{
                        script{
                           dir("D:\\JenkinsPipeline\\zjx_TestBuild\\"){
                               GitPipeline.CheckOutWithConfSetup(this, BuildArgs, BuildArgs.Submodule)
                                //bat("""git clone http://zengjianxiong:BEzengjianxiong@192.168.5.23:8090/BuildEngineer/Git/GitExtensions.git""")
                           }
                           
                        }
                    }
                } 
            }
    } 
}   