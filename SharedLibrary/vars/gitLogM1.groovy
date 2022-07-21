import lib.Utility
import lib.email.*
def call(List gitRoot, String platform,boolean isSubmodule = false){
    try{
        def ret = ""
        def t = Utility.OS(this).Terminal
        def cmdOriginURL = Utility.Git.OriginURL.CMD
        def cmdCurrentBranch = Utility.Git.CurrentBranch.CMD
        def cmdLogM1 = Utility.Git.LogM1.CMD
        
        if(isSubmodule){
            cmdOriginURL = Utility.Git.SubmoduleOriginURL.CMD
            cmdCurrentBranch = Utility.Git.SubmoduleCurrentBranch.CMD
            cmdLogM1 = Utility.Git.SubmoduleLogM1.CMD
        }
        def cmd = cmdOriginURL + " & " + cmdCurrentBranch + " & " + cmdLogM1
        gitRoot.each{
            dir(it){
                ret += "${t}"(script:cmd, returnStdout:true,encoding: 'UTF-8') + "\r\n"
            }
        }
        print ret
        def fn = "${env.BUILD_NUMBER}_GitPull.${platform}.html"
        writeFile text:EmailSender.generateGitChangeLogM1(ret),file:fn, encoding:"UTF-8"
        archiveArtifacts artifacts:fn, followSymlinks: false, fingerprint: true, allowEmptyArchive : true
        currentBuild.description +="""<br/><a href="${env.BUILD_URL}artifact/${fn}">${fn}</a>"""
    }
    catch(Exception ex){
        print ("git LogM1 failed")
    }

    // dir(env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Client){
    //     bat(returnStdout: true, script:Utility.Git.CurrentBranch.CMD+">"+env.GitRootAndroid+"\\AndroidGitPull.log")
    //     bat(returnStdout: true, script:Utility.Git.LogM1.CMD+">>"+env.GitRootAndroid+"\\AndroidGitPull.log")
    // }
    // dir(env.GitRootAndroid+"\\"+BuildArgs.CheckOutFolder.Framework){
    //     bat(returnStdout: true, script:Utility.Git.CurrentBranch.CMD+">>"+env.GitRootAndroid+"\\AndroidGitPull.log")
    //     bat(returnStdout: true, script:Utility.Git.LogM1.CMD+">>"+env.GitRootAndroid+"\\AndroidGitPull.log")
    // }
}