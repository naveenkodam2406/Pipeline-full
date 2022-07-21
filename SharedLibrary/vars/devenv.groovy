import lib.Utility

def call(kwargs=[:] ){
    def t = ""
    if(kwargs.shell){
        t = Utility.OS(this).Shell
    }else{
        t = Utility.OS(this).Terminal
    }
    if(kwargs.echo){
        t = "print"
    }
    if(kwargs.devenv == null) kwargs.devenv = """ "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\Common7\\IDE\\devenv.com" """
    
    "${t}"("${kwargs.devenv} ${kwargs.solution} ${kwargs.opt}")
}