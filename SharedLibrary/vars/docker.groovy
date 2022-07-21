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
    try{
        "${t}"("docker ${kwargs.script}")
    }
    catch(Exception ex){
        print("Failed when doing cleaning up, but not fail this build.")
        print ex
        if(kwargs.propagate){
            error(ex)
        }
    }
    
}