import lib.Utility
import lib.email.*
def call(List gitRoot, String platform,boolean isSubmodule = false){
    try{
        def ret = ""
        def t = Utility.OS(this).Terminal
        def cmd = Utility.Git.Status.CMD
        if(isSubmodule){
            cmd = Utility.Git.SubmoduleStatus.CMD
        }
        gitRoot.each{
            dir(it){
                ret += "${t}"(script:cmd, returnStdout:true,encoding: 'UTF-8') + "\r\n"
            }
        }
        print ret
        def fn = "${env.BUILD_NUMBER}_GitStatus.${platform}.html"
        writeFile text:EmailSender.generateGitStatus(ret),file:fn, encoding:"UTF-8"
        archiveArtifacts artifacts:fn, followSymlinks: false, fingerprint: true, allowEmptyArchive : true
        currentBuild.description +="""<br/><a href="${env.BUILD_URL}artifact/${fn}">${fn}</a>"""
    }
    catch(Exception ex){
        print ("git status failed")
    }
}