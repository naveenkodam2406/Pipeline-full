def call(path, type){
    return powershell (script: "gci -recurse \"${path}\"  -File -filter \"${type}\" | select -ExpandProperty FullName | Out-String", returnStdout: true).trim().tokenize("\r\n")
}
