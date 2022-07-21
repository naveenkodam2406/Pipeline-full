
import lib.*
def call(BuildArgs) {
    pipeline{
        agent{label BuildArgs.AssignedLabel}
        options {
            skipDefaultCheckout true
            timestamps()
            timeout(time: 5, unit: 'HOURS')
            buildDiscarder(logRotator(numToKeepStr: "7"))
        }
        stages{
            stage("Clean Folders"){
                steps{
                    script{
                        def parallelSteps = [:]
                        for (int i = 0; i < BuildArgs.Folders.size(); i++ ){
                            def item = BuildArgs.Folders[i]
                            parallelSteps[i] = {->l:{
                                cleanFolder(item.Path, item.KeepCount, item.Excluded?item.Excluded:"")
                                println env.deletedPath
                            } }
                        }
                        parallel(parallelSteps)
                    }
                }
            }
        }
        post{
            always {
                script{
                    Utility.CleanJobWorkSpace(this)
                }
            }
        }
    }
}