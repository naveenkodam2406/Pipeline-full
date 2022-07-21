import lib.Utility
// "DiskSpaceCheck":[
//     "androidbuildnode":["D","200GB"]
// ]

def call(Map DiskSpaceCheck){
    if(DiskSpaceCheck != null){
        DiskSpaceCheck.each{n,driveList ->l:{
            driveList.each{
                def ret = checkDiskFreeSize(n,it[0],it[1])
                if(ret == null){
                    print ("ERROR: " + n +" "+it[0] +" "+it[1] + " disk check failed, command exec with exceptions, please check log ")
                    error(n +" "+it[0] +" "+it[1] + " disk check failed, command exec with exceptions, please check log ") 
                }
                else if (ret == false){
                    print ("ERROR: " + n +" "+it[0] +" "+it[1] +  " disk check failed, free space is lower than the current threshold, quit the build")
                    error(n +" "+it[0] +" "+it[1] +  " disk check failed, free space is lower than the current threshold, quit the build")
                }
            }
        }}
    }
}
def call(String nodeLabel, String drive, String freeSize){
    try{
        node(nodeLabel){
            def osType = Utility.OS(this)
            if ( osType == Utility.MobilePlatform.WINDOWS ) {
                def rs = powershell (script: "Get-PSDrive $drive | where {\$_.Free /$freeSize -GT 1 } | Out-String", 
                returnStdout: true).toString()
                print (rs)
                if(rs.trim().length() == 0){
                    return false
                }
                else{
                    return true
                }
            }
            else { //Utility.MobilePlatform.UNIX
                // not yet implemented 
                return true
            }
        }
    }
    catch(Exception ex){
        return null  // command exec with exception
        print ex
    } 
}