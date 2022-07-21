import lib.Utility
def call(kwargs = [:]){
    if(kwargs.remote != null && kwargs.remote != "") {
        kwargs.remote = "-r ${kwargs.remote}"
    }
    else{
        kwargs.remote = ""
    }
    if(kwargs.port != null && kwargs.port != ""){
        kwargs.port = "-port ${kwargs.port}"
    }else{
        kwargs.port = ""
    }
    if(kwargs.opt == null) kwargs.opt = ""
    kwargs.path = Utility.OS(this).Path(kwargs.path)
    python(Utility.CopyGlobalLibraryScript(this, "scripts/FTPUpload.py"), """-host ${kwargs.host} -uid ${kwargs.uid} -pwd "${kwargs.pwd}" ${kwargs.remote} -p ${kwargs.path} ${kwargs.port} ${kwargs.opt}""", kwargs.python,true )
}