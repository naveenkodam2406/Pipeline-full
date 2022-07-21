import lib.email.*
def call(scmRS,assignedLabel){
    if (currentBuild.changeSets.size() > 0 && scmRS.size() > 0){
        node(assignedLabel){
            try{
                writeFile(file: "${env.BUILD_NUMBER}_Changes.html", text: EmailSender.generateReleaseNotes(this,scmRS), encoding: "UTF-8")
                archiveArtifacts artifacts: "${env.BUILD_NUMBER}_Changes.html", followSymlinks: false, onlyIfSuccessful: false  
                currentBuild.description +="""<br/><a href="${env.BUILD_URL}artifact/${env.BUILD_NUMBER}_Changes.html"><h1>${env.BUILD_NUMBER}_Changes.html</h1></a> """
            }
            catch(Exception ex){
                print ("Failed to generate Changes Page")
            }
        }
    }
}