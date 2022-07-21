import lib.Utility
def call(method, optionalArgs){
    try{
        python(Utility.CopyGlobalLibraryScript(this, "scripts\\Elastic.Query.py"), "${method} ${optionalArgs}")
    }
    catch(Exception ex){
        print (ex)
    }
}