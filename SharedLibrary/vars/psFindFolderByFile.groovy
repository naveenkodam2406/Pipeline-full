//"-Depth 1"
def call(path, file, depth="", extra=""){
    //https://issues.jenkins-ci.org/browse/JENKINS-50840
    //https://github.com/jenkinsci/durable-task-plugin/pull/70
    //broken while durable-task-plugin @1.25
    return powershell (script: "gci -recurse \"${path}\" -filter \"${file}\" ${depth} ${extra} | select Directory | ft -hidetableheaders | Out-String", returnStdout: true).trim()
}