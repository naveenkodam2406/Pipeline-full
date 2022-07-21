
import lib.Utility
def call(fileName, folder){
    if(env."${fileName}_FILENAME" == "${fileName}"){
        unstash fileName
        def bundleAsset = readFile(fileName)
        if(bundleAsset!=""){
            def path = Utility.OS(this).Path("${folder}\\${fileName}")
            writeFile(file:path, text:bundleAsset)
        }
    }
}