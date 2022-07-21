import lib.Utility


def call(desc, echo=true){
    
    def t = Utility.OS(this).Terminal
    cmdStagedFileList = Utility.Git.StagedFiles.CMD
    cmdPullRebase = Utility.Git.PullRebase.CMD
    cmdCommit = Utility.Git.Commit.CMD.replace("{DESC}", desc)
    cmdPush = Utility.Git.Push.CMD
    cmdCleanDf = Utility.Git.CleanDf.CMD
    cmdCheckOutCurrent = Utility.Git.CheckOutCurrent.CMD
    if(echo){
        t = "print"
        cmdCommit = "echo " + cmdCommit
        cmdPullRebase = "echo " + cmdPullRebase
        cmdPush = "echo " + cmdPush
        cmdCleanDf = "echo " + cmdCleanDf
        cmdCheckOutCurrent = "echo " + cmdCheckOutCurrent
    }
    try{
        def list = []
        if(t != "print"){
            list = "${t}"(script: cmdStagedFileList, returnStdout: true).trim().tokenize("\r\n")

        }
        if(list.size()>1 || echo){ // the first line is the command itself
            "${t}"(cmdCommit) // in case there is nothing to commit
            "${t}"(cmdCleanDf)
            "${t}"(cmdCheckOutCurrent)
            "${t}"(cmdPullRebase)
            "${t}"(cmdPush)
        }
    }
    catch(Exception ex){
        //print out but not fail the build
        print ex
    }
    
    
}