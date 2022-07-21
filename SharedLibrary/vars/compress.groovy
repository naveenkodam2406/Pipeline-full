import lib.Utility
def call (destZipFile, sourceFolder){
    def zipFile = destZipFile.tokenize("\\").last()
    try{
        tool7z( "a -tzip  -mx5 -mmt4 ${destZipFile}.compressWIP ${sourceFolder} -y")
        bat "REN ${destZipFile}.compressWIP ${zipFile}"
    }
    catch(Exception e){
        bat "REN ${destZipFile}.compressWIP ${zipFile}.Exception"
        println "Error occured while compressing to ${destZipFile}.compressWIP"
        throw e
    }
}
def call(kwargs = [:]){
    if (kwargs.mode == null ) kwargs.mode = 0
    python(Utility.CopyGlobalLibraryScript(this, "scripts/Compress.py"), "--zip ${kwargs.zip} --path ${kwargs.path} --mode ${kwargs.mode} --relpath ${kwargs.relpath}", kwargs.python )
}