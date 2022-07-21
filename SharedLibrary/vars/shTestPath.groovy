def call(path){
    return sh (script: """[ -d "${path}" ] && echo 1 || echo 0 """, returnStdout: true).trim().toBoolean()
}