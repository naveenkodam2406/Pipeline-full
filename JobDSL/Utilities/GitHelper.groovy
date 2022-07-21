package Utilities

class GitHelper{
    static gitSCM(scmContext, agrsMap){
        scmContext.with{
            scm{
                git {
                    remote {
                        branches(agrsMap.branch)
                        url(agrsMap.url)
                        credentials(agrsMap.credential)
                    }
                }
            }
        }
    }
}