def call(SourcePath, TargetPath){
    def SourceFolders = powershell (script: "dir -path \"${SourcePath}\" -Directory | Select -ExpandProperty Name | Out-String ", returnStdout: true).trim().tokenize("\r\n").sort()
    def TargetFolders = powershell (script: "dir -path \"${TargetPath}\" -Directory | Select -ExpandProperty Name | Out-String ", returnStdout: true).trim().tokenize("\r\n").sort()
    def NewFolders = []
    boolean isNew = true
    for (int i = 0; i<SourceFolders.size(); i++){
        for (int j = 0; j<TargetFolders.size(); j++){
            if (SourceFolders[i]==TargetFolders[j]){
                isNew = false
            }
        }
        if (isNew){
            NewFolders.add(SourceFolders[i])
        }
        isNew = true
    }
    return NewFolders
}