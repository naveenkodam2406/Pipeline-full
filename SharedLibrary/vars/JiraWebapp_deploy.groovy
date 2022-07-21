import lib.*

def call(BuildArgs) {  
    pipeline{
        agent {label BuildArgs.buildAgent}
        options {
            timestamps()
            timeout(time: 5, unit: 'HOURS')
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "20"))
        }
        stages{
            stage("Git Pull"){
                steps{
                    script{
                        dir(BuildArgs.buildRoot + '\\jira_resouce_project'){
                            bat("git fetch")
                            bat("git pull")
                        }
                    }
                }
            }
            stage("Transfer_Crackfile_to_Jirabuildresult"){
                steps{
                    script{
                        dir(BuildArgs.buildRoot + "\\jira_resouce_project\\tools"){
                            bat("copy atlassian-extras-3.2.jar " + BuildArgs.buildRoot + "\\jira_resouce_project\\jira-project\\jira-components\\jira-webapp\\target\\jira\\WEB-INF\\lib")
                        }
                    }
                }
            }
            stage("modify_jira.home"){
                steps{
                    script{
                        dir(BuildArgs.buildRoot + "\\jira_resouce_project\\jira-project\\jira-components\\jira-webapp\\target\\jira\\WEB-INF\\classes"){
                            bat("del -F jira-application.properties")
                            bat("echo jira.home = " + BuildArgs.JiraDatabasePath + ">>jira-application.properties")
                        }
                    }
                }
            }
            stage("Transfer_Jirabuildresult_to_JiraAppPath"){
                steps{
                    script{
                        dir(BuildArgs.JiraAppPath){
                            bat("del /F /q /s *.*")
                            bat("xcopy " + BuildArgs.buildRoot + "\\jira_resouce_project\\jira-project\\jira-components\\jira-webapp\\target\\jira\\* " + '"'+BuildArgs.JiraAppPath +'"'+" /y /e /i /q")
                        }
                    }
                }
            }
            stage("cleanup_Workspace"){
                steps{
                    script{
                        dir(BuildArgs.buildRoot + "\\jira_resouce_project"){
                            //bat("git reset --hard origin/main")
                            bat("git reset head")
                        }
                    }
                }
            }
        }
    }
}