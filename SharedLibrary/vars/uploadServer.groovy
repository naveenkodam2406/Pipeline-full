import groovy.json.JsonSlurper
import lib.Utility

def call(kwargs = [:]){
    def fullName = kwargs.path  + "\\" +kwargs.file
    if(kwargs.file == null){
        fullName = kwargs.path
        kwargs.file = " /E /S "// for robocopy folder
    }
    if(kwargs.subLocalFolder != null){
        fullName = fullName +"/"+ kwargs.subLocalFolder
    }
    def internalIPReg = "192..*"
    def serverConf = Utility.JsonStrToObj(libraryResource(kwargs.serverConfPath))
    if(!serverConf.Server."${kwargs.serverTag}") return 
    serverConf.Server."${kwargs.serverTag}".each{ip, remoteDir->l:{
        if(kwargs.subRemote !=null ){
            remoteDir = remoteDir +"/"+ kwargs.subRemote
        }
        if(Utility.RegExFind(ip, internalIPReg)){
            // internal just copy
            remoteDir = remoteDir.replace("/","\\")
            if(!remoteDir.startsWith("\\")) remoteDir = "\\"+remoteDir
            robocopy(kwargs.path, "\\\\"+ip+remoteDir, kwargs.file)
            
        }else{
            withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remoteDir, path:fullName)
            }
            
        }
    }}
}