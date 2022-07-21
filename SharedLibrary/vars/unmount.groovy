def call (mountedFolder){
    if(mountedFolder != null){
        try{
            sh("umount ${mountedFolder[1]}")
            sh("rmdir ${mountedFolder[1]}")
        }
        catch(Exception ex){
            print ("umount Failed")
        }
    }
}
