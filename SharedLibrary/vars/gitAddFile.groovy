import lib.Utility


def call(List files,boolean echo=true){
    // to split the list into 5, to avoid the cmdline exceeding the max length of the os could take.
    def result = files.collate( files.size().intdiv( 5 ), false ) // false, if the last one is smaller than the required size, put it into the last second one.
    result.each{
        gitAddFile(it.join(" "), echo)
    }
}

def call(String files, boolean echo=true){
    def t = Utility.OS(this).Terminal
    cmd = Utility.Git.Add.CMD
    files = Utility.OS(this).Path(files)
    if(echo){
        t = "print"
        cmd = "echo " + cmd
    }
    "${t}"(cmd + files)
}