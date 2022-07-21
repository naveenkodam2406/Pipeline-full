import lib.Utility
def call(folderName){
    dir(Utility.OS(this).Path(folderName)){
        archiveArtifacts artifacts: "**\\*", followSymlinks: false, fingerprint: true, allowEmptyArchive : true
    }
}

