def call(source,destination,options=""){
    if (options==null) options=""
    bat (script: """robocopy ${source} ${destination} ${options} /NP /R:2 /W:5 /MT:8
           |IF %ERRORLEVEL% GEQ 0 IF %ERRORLEVEL% LSS 8 ( EXIT 0 ) ELSE ( EXIT %ERRORLEVEL% )""".stripMargin())

}

def call(kwargs=[:]){
    def host = kwargs.Dest.tokenize("\\")[0]
    def mounted = false
    if (kwargs.Username != null && kwargs.Password != null){
        bat("net use \\\\${host} /u:${kwargs.Username} ${kwargs.Password}")
        mounted = true
    }
    try{
        robocopy(kwargs.Src, kwargs.Dest,kwargs.Opt)
    }
    finally{
        if(mounted){
            bat("net use \\\\${host} /d")
        }
    }
}