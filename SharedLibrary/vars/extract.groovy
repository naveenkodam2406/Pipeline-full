def call (source, destination, removeIfExist=true){
    def fileName = source.tokenize("\\").last()
    def folderName = fileName.replace("."+fileName.tokenize(".").last(),"")
    try{
        if(removeIfExist == true){
            dir("${destination}\\${folderName}"){
                deleteDir()
            }
        }
        tool7z( "x ${source} -o${destination}\\${folderName}.extractWIP -y")
        bat "REN ${destination}\\${folderName}.extractWIP ${folderName}"
        return "${destination}\\${folderName}"
    }
    catch(Exception e){
        bat "REN ${destination}\\${folderName}.extractWIP ${folderName}.Exception"
        bat "REN ${source} ${source}.Exception"
        println "Error occured while extracting to ${destination}.extractWIP"
        throw e
    }
}