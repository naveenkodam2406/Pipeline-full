import lib.Utility


def call(opt){
    // to split the list into 3, to avoid the cmdline exceeding the max length of the os could take.
    def t = Utility.OS(this).Terminal
    cmd = Utility.Git.Branch.CMD + opt
    "${t}"(cmd)
}