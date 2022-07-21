import lib.Utility

def call(path, opt, echo=false){
    def t = Utility.OS(this).Terminal
    path = Utility.OS(this).Path(path)
    cmd = Utility.Git.CleanDf.CMD
    
    if(echo){
        t = "print"
        cmd = "echo " + cmd
    }
    "${t}"(cmd + " ${opt} ${path}")
}