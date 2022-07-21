import lib.Utility

def call(clientProjPath, frameworkPath){
    def t = Utility.OS(this).Terminal
    try{
        if(t=="bat"){
            bat("""if exist "${clientProjPath}\\trunk\\GameSharp" rmdir "${clientProjPath}\\trunk\\GameSharp" /q /s""")
            bat(""" MKLINK /J "${clientProjPath}\\trunk\\GameSharp" "${frameworkPath}\\trunk\\GameSharp" """)
            bat("""if exist "${clientProjPath}\\trunk\\Client\\TargetProject\\Assets\\BJFramework" rmdir "${clientProjPath}\\trunk\\Client\\TargetProject\\Assets\\BJFramework" /q /s""")
            bat(""" MKLINK /J "${clientProjPath}\\trunk\\Client\\TargetProject\\Assets\\BJFramework" "${frameworkPath}\\trunk\\BJFramework2\\Assets\\BJFramework" """)
            bat("""if exist "${clientProjPath}\\trunk\\Client\\TargetProject\\Assets\\Plugins\\BJFramework" rmdir "${clientProjPath}\\trunk\\Client\\TargetProject\\Assets\\Plugins\\BJFramework" /q /s""")
            bat(""" MKLINK /J "${clientProjPath}\\trunk\\Client\\TargetProject\\Assets\\Plugins\\BJFramework" "${frameworkPath}\\trunk\\BJFramework2\\Assets\\Plugins\\BJFramework" """)
        }
        else if(t =="sh"){
            sh("""[ -d "${clientProjPath}/trunk/GameSharp" ] && rm -rf "${clientProjPath}/trunk/GameSharp" || echo GameSharp does not exist """)
            sh(""" ln -s -v  "${frameworkPath}/trunk/GameSharp" "${clientProjPath}/trunk/GameSharp" """)

            sh("""[ -d "${clientProjPath}/trunk/Client/TargetProject/Assets/BJFramework" ] && rm -rf "${clientProjPath}/trunk/Client/TargetProject/Assets/BJFramework" || echo Assets/BJFramework does not exist """)
            sh(""" ln -s -v  "${frameworkPath}/trunk/BJFramework2/Assets/BJFramework" "${clientProjPath}/trunk/Client/TargetProject/Assets/BJFramework" """)

            sh("""[ -d "${clientProjPath}/trunk/Client/TargetProject/Assets/Plugins/BJFramework" ] && rm -rf "${clientProjPath}/trunk/Client/TargetProject/Assets/Plugins/BJFramework" || echo Plugins/BJFramework does not exist  """)
            sh(""" ln -s -v  "${frameworkPath}/trunk/BJFramework2/Assets/Plugins/BJFramework" "${clientProjPath}/trunk/Client/TargetProject/Assets/Plugins/BJFramework" """)
        }
    }
    catch(Exception ex){
        print ex
        unstable("MKLINK failed")
    }
    
}