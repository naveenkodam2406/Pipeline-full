/*
env.ProjectPathClient
env.OutputShare
env.OutputShareAB
env.BUILD_NUMBER
*/
import lib.Utility

def call(kwargs=[:]){
    def os = Utility.OS(this)
    def eABPath = Utility.OS(this).Path(kwargs.GitRoot +"\\"+env.ProjectPathClient+"\\ExportAssetBundle")
    if(kwargs.Compress == null ) kwargs.Compress = true
    if(kwargs.Python == null) kwargs.Python = "/usr/local/bin/python"
    if(kwargs.Compress){
        if (os == Utility.MobilePlatform.WINDOWS){
            if(psTestPath(eABPath)){
                compress(zip:"${kwargs.OutputRoot}\\ExportAssetBundle.zip" , path:eABPath)
            }
        }
        else if (os == Utility.MobilePlatform.UNIX){
            if(shTestPath(eABPath)){
                compress(zip:"${kwargs.OutputRoot}/ExportAssetBundle.zip" , path:eABPath, python: kwargs.Python)
            }
        }
    }
    else{
        if (os == Utility.MobilePlatform.WINDOWS){
            if(psTestPath(eABPath)){
                robocopy(eABPath,kwargs.OutputRoot+"\\ExportAssetBundle"," /E /S ")
            }
        }
        else if (os == Utility.MobilePlatform.UNIX){
            if(shTestPath(eABPath)){
                rsync("${eABPath}", "${kwargs.OutputRoot}","*")
            }
        }
    }
    
}