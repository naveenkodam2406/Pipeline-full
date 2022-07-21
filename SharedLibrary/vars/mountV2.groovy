import lib.Utility

def call (kwargs = [:]){
    if (!kwargs.path || !kwargs.cred) return null
    def fileMaps = [:]
    def serverMaps = [:]
    def splitP = kwargs.path.tokenize("\\")
    def serverPath = splitP[0]+"/"+ splitP[1]
    if(splitP.size > 3){
        serverPath = splitP[0]+"/"+ splitP[1] + "/" + splitP[2]
    }
    def mountedFolder = serverPath.replace("/",".")
    def filePath = ""
    splitP.takeRight(splitP.size - 2).each{p->j:{
        filePath += "/${p}"
    }}

    if(splitP.size > 3){
        filePath =""
        splitP.takeRight(splitP.size - 3).each{p->j:{
            filePath += "/${p}"
        }}
    }

    fileMaps[filePath] = serverPath
    print("Credential of Network Mount: " + kwargs.cred)
    def credId = Utility.GetcredentialByDescription(kwargs.cred)
    if(credId == null){
        error("Failed to find out a credentail with the given description: " + kwargs.cred)
    }

    def tmpPath = "$TMPDIR/" + mountedFolder
    if(!shTestPath(tmpPath)){
        sh(script:"mktemp -d ${tmpPath}")
    }
    withCredentials([usernameColonPassword(credentialsId: credId ,variable: 'UserPass')]) {
        //https://www.degraeve.com/reference/urlencoding.php
        env.UserPass = UserPass.replace("%","%25").replace("#","%23").replace("'","%27")
        .replace("!","%21").replace("\$","%24").replace("?","%3F")
        .replace("=","%3D").replace("\\","%5C")
        // moving the mount cmd out of the withCredentials block, cau'z we need to manipulate the chars for shell
        //sh('mount_smbfs -d 777 -f 777 "//$UserPass'+'@'+serverPath+'" "'+temp+'"')
        //serverMaps[serverPath] = temp
    }
    try{
        sh("mount_smbfs -d 777 -f 777 //${env.UserPass}@${serverPath} ${tmpPath}")
    }
    catch(Exception ex){
        print (ex)
        print("${env.UserPass}@${serverPath} has already been mounted")
    }
    serverMaps[serverPath] = tmpPath
    return ["${tmpPath}${filePath}",tmpPath,serverPath]
}