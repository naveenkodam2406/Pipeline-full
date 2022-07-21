import lib.Utility

def call(kwargs = [:]){
    if(kwargs.path){
        python(Utility.CopyGlobalLibraryScript(this, "scripts/MDFive.py"), "--path ${kwargs.path} --relpath ${kwargs.relpath}", kwargs.python)
    }
    else if(kwargs.file){
        python(Utility.CopyGlobalLibraryScript(this, "scripts/MDFive.py"), "--file ${kwargs.file}", kwargs.python)
    }
    
}