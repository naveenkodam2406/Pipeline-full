import lib.Utility

def call(serverProjPath, frameworkPath){
    def t = Utility.OS(this).Terminal
    try{
        if(t=="bat"){
            bat("""if exist "${serverProjPath}\\GameSharp" rmdir "${serverProjPath}\\GameSharp" /q /s""")
            bat(""" MKLINK /J "${serverProjPath}\\GameSharp" "${frameworkPath}\\trunk\\GameSharp" """)
        }
        else if(t =="sh"){
            sh("""[ -d "${serverProjPath}/GameSharp" ] && rm -rf "${serverProjPath}/GameSharp" || echo GameSharp does not exist """)
            sh(""" ln -s -v  "${frameworkPath}/trunk/GameSharp" "${serverProjPath}/GameSharp" """)
        }
    }
    catch(Exception ex){
        print ex
        unstable("MKLINK failed")
    }
    
}