def call(folder){
    return powershell (script: "(dir \"${folder}\" | measure).Count", returnStdout: true)     
}