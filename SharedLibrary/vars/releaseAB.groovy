/*
env.ProjectPathClient
env.OutputShare
env.OutputShareAB
env.BUILD_NUMBER
*/
import lib.Utility

def call(kwargs=[:]){
    def os = Utility.OS(this)
    if (os == Utility.MobilePlatform.WINDOWS){
        robocopy(kwargs.GitRoot +"\\"+env.ProjectPathClient+"\\AssetBundles",kwargs.OutputRoot+"\\AssetBundles"," /XF *.manifest /E /S ")
        robocopy(kwargs.GitRoot +"\\"+env.ProjectPathClient+"\\AssetBundles",kwargs.OutputRoot+"\\manifest"," *.manifest /E /S ")
        mdFive(path:kwargs.OutputRoot+"\\AssetBundles")
        robocopy(kwargs.OutputRoot+"\\AssetBundles",env.OutputShare+"\\"+env.OutputShareAB," /XF *.manifest VerifyMd5.txt /E /S ")
        robocopy(kwargs.OutputRoot+"\\manifest",env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}"," *.manifest /E /S ")
        return readFile("${kwargs.OutputRoot}\\AssetBundles\\VerifyMd5.txt")
    }
    else if (os == Utility.MobilePlatform.UNIX){
        rsync("${kwargs.GitRoot}/${env.ProjectPathClient}/AssetBundles/", "${kwargs.OutputRoot}/manifest","*/ *.manifest","*")
        rsync("${kwargs.GitRoot}/${env.ProjectPathClient}/AssetBundles/", "${kwargs.OutputRoot}/AssetBundles","*/","*.manifest")
        sh("find ${kwargs.OutputRoot} -name '.DS_Store' -type f -delete")
        mdFive(path:kwargs.OutputRoot+"/AssetBundles", python:"/usr/local/bin/python")
        sh("mkdir -p ${kwargs.MountedFolder}/${env.OutputShareAB}")
        rsync("${kwargs.OutputRoot}/manifest/", "${kwargs.MountedFolder}/${env.OutputShareAB}/manifest_${env.BUILD_NUMBER}","*/ *.manifest","*")
        rsync("${kwargs.OutputRoot}/AssetBundles/", "${kwargs.MountedFolder}/${env.OutputShareAB}","*/","*.manifest VerifyMd5.txt")
        sh("find ${kwargs.MountedFolder} -name '.DS_Store' -type f -delete")
        return readFile("${kwargs.OutputRoot}/AssetBundles/VerifyMd5.txt")
    }
    
    
}