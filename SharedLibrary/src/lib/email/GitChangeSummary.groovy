package lib.email

import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.workflow.rest.external.RunExt
import hudson.Functions
import java.util.concurrent.TimeUnit

class GitChangeSummary {

    static final MAX_NUM_OF_CHANGELIST_FILES = 5
    static final CHANGELIST_DESC_LENGTH_MAX = 100

    @NonCPS
    static void recurseGitURL(builder, ScmEnvList , commitId, processedGit=[:]){
        try{
            if(ScmEnvList instanceof List){
                for(int k = 0; k < ScmEnvList.size(); k++){
                    recurseGitURL(builder, ScmEnvList[k], commitId,processedGit)
                }
            }else{
                if(ScmEnvList.ScmEnv){
                    def gitInfo = ScmEnvList.ScmEnv
                    if( gitInfo.GIT_COMMIT == commitId && !processedGit.containsKey(gitInfo.GIT_URL)){
                        builder.SH { builder.mkp.yield("Summary")}
                        builder.SSH{ builder.mkp.yield(gitInfo.GIT_URL) }
                        processedGit[gitInfo.GIT_URL]="" // so we know it is processed
                    }
                }
                if(ScmEnvList.Submodules){
                    recurseGitURL(builder, ScmEnvList.Submodules, commitId, processedGit)
                }
            }
        }   
        catch(Exception ex){
            print ex
        }
    }
    @NonCPS
    static void generateGitChangeSetReport(builder, env, currentBuild, ScmEnvList=[]) {
        // 1. changes
        if (currentBuild.changeSets.size()) {
            builder.table { builder.tr{ builder.TD(class: "changes_header") { b { builder.mkp.yield("Changes") } } } }
            
            def changeLogSets = currentBuild.changeSets
            def processedGit = [:]
            for (int i = 0; i < changeLogSets.size(); i++) {
                def entries = changeLogSets[i].items
                // 2. summary for each
                builder.SUMMARY{
                    if(ScmEnvList != null && ScmEnvList.size()>0){
                        recurseGitURL(builder,ScmEnvList, entries[(entries.size()-1)].id,processedGit)
                    }
                }
                builder.SUMMARY{
                    builder.OL{
                        // 3. bullet point commit desc && detail commit in table
                        for (int j = (entries.length-1); j >= 0; j--) {
                            def entry = entries[j]
                            def message = ""
                            if (entry.msgAnnotated) {
                                message = entry.msgAnnotated
                                message = message.replaceAll(~/(?i)(<BR>|&nbsp;|\s)/, " ")
                                if (message.length() > CHANGELIST_DESC_LENGTH_MAX ) {
                                    message = message.substring(0, CHANGELIST_DESC_LENGTH_MAX + 1)+"..."
                                }
                            }
                            builder.LI {
                                builder.mkp.yield(message+' (') 
                                builder.a(href:"#${entry.id}",'detail')
                                builder.mkp.yield(')')
                            }
                        }
                    }
                }
                builder.TABLE{
                    for (int j = (entries.length-1); j >= 0; j--) {
                        def entry = entries[j]
                        def message = ""
                        if (entry.msgAnnotated) {
                            message = entry.msgAnnotated
                            message = message.replaceAll(~/(?i)(<BR>|&nbsp;|\s)/, " ")
                            if (message.length() > CHANGELIST_DESC_LENGTH_MAX ) {
                                message = message.substring(0, CHANGELIST_DESC_LENGTH_MAX + 1)+"..."
                            }
                        }
                        builder.TR {
                            builder.TD(class: "changes_since_previous") {
                                builder.mkp.yield("Revision")
                                builder.b { 
                                    builder.a(id: "${entry.id}",entry.id)
                                }
                                builder.span {
                                    builder.mkp.yield("by")
                                    builder.b { builder.mkp.yield(entry.author) }
                                    def checkinTime = new Date(entry.timestamp).format('dd/MM/yyyy HH:mm:ss')
                                    builder.mkp.yield(" at ${checkinTime}")
                                }
                                builder.br{}
                                builder.span {
                                    builder.mkp.yield(message)
                                }
                            }
                        }

                        def files = new ArrayList(entry.affectedFiles)
                        for (int k = 0; k < files.size(); k++) {
                            if (k == MAX_NUM_OF_CHANGELIST_FILES ) {
                                builder.tr { builder.TD { builder.mkp.yield("... ${files.size()} files in this Change  ") } }
                                break
                            }

                            def file = files[k]
                            builder.TR {
                                builder.TD {
                                    builder.mkp.yield("${file.editType.name}: ${file.path}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @NonCPS
    static void generateGitStatusReport(builder,String gitStatusStd){
        builder.PRE{
            gitStatusStd.tokenize("\r\n").each{
                if (
                    (
                        it.toLowerCase().contains(".meta") //|| it.toLowerCase().contains("modified:") || it.toLowerCase().contains("deleted:")
                    ) 
                    && !it.toLowerCase().contains("@tmp")
                    ) 
                {
                    builder.WARNING(it)
                }
                else{
                    builder.mkp.yield(it)
                }
                builder.br()
            }
        }
    }
}