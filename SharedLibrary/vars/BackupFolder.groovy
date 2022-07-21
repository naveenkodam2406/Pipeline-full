
import lib.*
def call(BuildArgs) {
    pipeline{
        agent{label BuildArgs.AssignedLabel}
        options {
            skipDefaultCheckout true
            timestamps()
            buildDiscarder(logRotator(numToKeepStr: "7"))
        }
        stages{
            stage("Backup Folders"){
                steps{
                    script{
                        for (int i = 0; i < BuildArgs.Folders.size(); i++ ){
                            def item = BuildArgs.Folders[i]
                            print "Back up info"
                            print item
                            
                            excluded =""
                            if (item.Excluded != "" && item.Excluded != null ) excluded = "--excluded ${item.Excluded}"
                            def move = ""
                            if(item.Move == true) move = "--move"
                            python(Utility.CopyGlobalLibraryScript(this, "scripts\\CleanFolder.py"), "--path ${item.BackupFrom} --keepCount ${item.KeepCount} ${excluded} --backupPath ${item.BackupTo} ${move}")
                        }
                    }
                }
            }
        }
        // post{
        //     always {
        //         script{
        //             Utility.CleanJobWorkSpace(this)
        //         }
        //     }
        // }
    }
}