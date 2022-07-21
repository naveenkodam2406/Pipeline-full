def call(path){
    return powershell (script: "Test-Path ${path}", returnStdout: true).trim().toBoolean()
}