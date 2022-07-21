import lib.Utility

def call(branchName){
    def t = Utility.OS(this).Terminal
    def cmd = Utility.Git.ResetHardNoneHEAD.CMD
    "${t}"(cmd)
    try{
        cmd = Utility.Git.Branch.CMD + """ -D "${branchName}" """
        "${t}"(cmd)
    }
    catch(Exception ex){
        print ex
    }
    cmd = Utility.Git.CheckOutWithOpts.CMD + """ -b "${branchName}" "origin/${branchName}" """
    "${t}"(cmd)
    cmd = Utility.Git.ResetHardNoneHEAD.CMD +""" origin/${branchName} """
    "${t}"(cmd)
    cmd = Utility.Git.CleanDf.CMD
    "${t}"(cmd)
    cmd = Utility.Git.Pull.CMD +""" origin ${branchName} """
    "${t}"(cmd)
}