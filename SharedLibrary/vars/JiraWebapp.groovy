import lib.*
import lib.Utility
import java.util.*;  
import java.util.Arrays;  
//import groovyx.net.http.HTTPBuilder;

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
                        dir(BuildArgs.buildRoot + '\\blackjack_jira'){
                            bat("git fetch")
                            bat("git pull")
                        }
                    }
                }
            }
            stage("unzip localrep"){
                steps{
                    script{
                        dir(BuildArgs.buildRoot + "\\blackjack_jira"){
                            bat("7z x -y " + BuildArgs.buildRoot + "\\blackjack_jira\\localrepo.zip" + " -o"+ BuildArgs.buildRoot + "\\blackjack_jira")
                        }
                    }
                }
            }
            stage("build_jirawebapp"){
                steps{
                    script{
                        dir(BuildArgs.buildRoot + "\\blackjack_jira"){
                            output = bat(returnStdout: true, script: BuildArgs.buildRoot + "\\blackjack_jira\\build.bat")
                            echo output
                        }
                    }
                }
            }
            stage("TransTarget_ToOutput"){
                steps{
                    script{
                        dir(BuildArgs.buildRoot + "\\blackjack_jira\\jira-project\\jira-components\\jira-webapp"){     
                            robocopy(BuildArgs.buildRoot + "\\blackjack_jira\\jira-project\\jira-components\\jira-webapp\\target", BuildArgs.TargetRoot + "\\target" +"_"+ env.BUILD_NUMBER + "_" + Utility.GetCurrentDate("yyyyMMdd"),"/E")                            
                        }
                    }
                }
            }
            stage("delete oldTargetFile"){
                steps{
                    script{
                        dir(BuildArgs.TargetRoot){                            
                            def FileListsStr = bat(returnStdout: true, script:"dir /B")
                            def FileList = FileListsStr.split("\n")//文件名存入一个list
                            FileList = FileList[2..-1]
                            if(FileList.size() >= 5){
                                def Map_file_changeTime = [:]
                                for (item in FileList){
                                    Map_file_changeTime[item] =  Integer.parseInt(item.split("_")[1]) //获取build版本号
                                }
                                def NumList = Map_file_changeTime.values() 
                                NumList = NumList.sort()
                                NeedcleanNumList = NumList[0..-5]
                                for(itemNum in  NeedcleanNumList){
                                    for (item in Map_file_changeTime.keySet()){
                                        if (Map_file_changeTime[item] == itemNum){
                                            bat("rmdir /S /Q ${item}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            stage("PostFilesListToELSSearch"){
                steps{
                    script{
                        dir(BuildArgs.TargetRoot){  
                            def FileListsStr = bat(returnStdout: true, script:"dir /B")
                            def FileList = FileListsStr.split("\n")//文件名存入一个list
                            def oldFileList = FileList[2..-1] 
                            def NewFileList = []
                            for (item in oldFileList){
                                item = item.replace("\n", "")
                                item = item.replace("\r", "")
                                NewFileList.add(item)
                            }
                            //echo NewFileList
                            def url = "http://192.168.5.100:9200/jiraversion/_doc/files"
                            def body = "{'jiraversion':'${NewFileList}'}"
                            // def body = "{'jiraversion':'${FileList}'}"
                            // def url= "http://192.168.5.100:9200/jiraversion/_doc/1"
                            // bat("curl -X POST -H 'Content-Type:application/json' -d ${body} ${url}")
                            elasticQuery("addToElastic", """ --url http://192.168.5.100:9200/jiraversion/_doc/files --data "${body}" """)                            
                        }
                    }
                }
            }
            stage("cleanup_Workspace"){
                steps{
                    script{
                        dir(BuildArgs.buildRoot + "\\blackjack_jira"){
                            bat("git reset --hard")
                            bat("git reset head")
                        }
                    }
                }
            }
        }
    }
}