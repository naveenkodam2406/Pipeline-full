/*
env.ProjectPathClient
env.OutputShare
env.OutputShareAB
env.BUILD_NUMBER
*/
import lib.Utility

def call(kwargs=[:]){
    def os = Utility.OS(this)
    def resavePath = Utility.OS(this).Path(kwargs.GitRoot +"\\"+env.ProjectPathClient+"\\Resave")
    if(kwargs.Compress == null ) kwargs.Compress = true
    if(kwargs.Python == null) kwargs.Python = "/usr/local/bin/python"
    if(kwargs.Compress == true){
        if (os == Utility.MobilePlatform.WINDOWS){
            if(psTestPath(resavePath)){
                compress(zip:"${kwargs.OutputRoot}\\Resave.zip" , path:resavePath)
            }
        }
        else if (os == Utility.MobilePlatform.UNIX){
            if(shTestPath(resavePath)){
                compress(zip:"${kwargs.OutputRoot}/Resave.zip" , path:resavePath, python: kwargs.Python)
            }
        }
    }
    else{
        if (os == Utility.MobilePlatform.WINDOWS){
            if(psTestPath(resavePath)){
                robocopy(resavePath,kwargs.OutputRoot+"\\Resave"," /E /S ")
            }
        }
        else if (os == Utility.MobilePlatform.UNIX){
            if(shTestPath(resavePath)){
                rsync("${resavePath}", "${kwargs.OutputRoot}","*")
            }
        }
    }
    
}