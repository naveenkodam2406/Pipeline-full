package lib.email

import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.workflow.rest.external.RunExt
import hudson.Functions
import java.util.concurrent.TimeUnit

class HTMLGenerator {

    static final MAX_NUM_OF_CHANGELISTS = 100
    static final MAX_NUM_OF_CHANGELIST_FILES = 5
    static final CHANGELIST_DESC_LENGTH_MAX = 100
    static String PRIORITIZED_EMAIL_GROUP = ""
    static boolean TO_RESTART_NODE = false
    static String PATTERN = ".*(error|exception|fatal|fail(ed|ure)|un(defined|resolved)|unable|java.).*"
    @NonCPS
    static void generateReportHeader(builder, env, currentBuild) {

        if (env.WARNING){
            builder.table {
                builder.tr {
                    builder.td { builder.mkp.yield("WARNING:") }
                    builder.td {  builder.pre{builder.b {builder.mkp.yield("${env.WARNING}") }}}
                }
            }
            builder.br {}
        }

        builder.table {
            builder.tr {
                builder.td(valign: "center", colspan: 2, class: "build_${currentBuild.currentResult.toLowerCase()}") {
                    builder.b { builder.mkp.yield("BUILD ${currentBuild.currentResult}") }
                }
            }
            builder.tr {
                builder.td { builder.mkp.yield("Project:") }
                builder.td { builder.mkp.yield("${env.JOB_NAME}") }
            }
            builder.tr {
                builder.td { builder.mkp.yield("Build No.:") }
                builder.td { builder.mkp.yield("${currentBuild.getNumber()}") }
            }
            builder.tr {
                builder.td { builder.mkp.yield("Build Name:") }
                builder.td { builder.mkp.yield("${currentBuild.displayName}") }
            }
            if (currentBuild.description) {
            def desc = currentBuild.description.replace("<h1>","").replace("</h1>","") // removing header style in email
            builder.tr {
                builder.td { builder.mkp.yield("Description:") }
                builder.td { builder.mkp.yieldUnescaped(desc)}
                }
            }
            builder.tr {
                builder.td { builder.mkp.yield("Build Report:") }
                builder.td {
                    builder.a(href: "${env.RUN_DISPLAY_URL}") {
                        builder.mkp.yield("${env.RUN_DISPLAY_URL}")
                    }
                }
            }
            builder.tr {
                builder.td { builder.mkp.yield("Job Summary:") }
                builder.td {
                    builder.a(href: "${env.BUILD_URL}JobSummary") {
                        builder.mkp.yield("${env.BUILD_URL}JobSummary")
                    }
                }
            }
            builder.tr {
                builder.td { builder.mkp.yield("Date of build:") }
                def timestamp = Functions.rfc822Date(currentBuild.getRawBuild().getTimestamp())
                builder.td { builder.mkp.yield(timestamp) }
            }
            builder.tr {
                builder.td { builder.mkp.yield("Build duration:") }
                builder.td { builder.mkp.yield(currentBuild.durationString) }
            }

        }
    }
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
                        builder.tr {
                            builder.td(class: "changes_header") { b { builder.mkp.yield(gitInfo.GIT_URL) } }
                            processedGit[gitInfo.GIT_URL]="" // so we know it is processed
                        }
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
    static void generateSvnChangeSetReport(builder, env, currentBuild, ScmEnvList=[]) {
        builder.br {}
        if (env.P4Unshelved){
            builder.table {
                builder.td(class: "shelved_change_header") { b { builder.mkp.yield("Shelved Change") } }
                builder.tr {
                    builder.td(class: "shelved_change_revision") {
                        builder.mkp.yield("Revision")
                        builder.b { builder.mkp.yield(env.ShelvedRevision) }
                    }
                    builder.span {
                        builder.mkp.yield("Unshelved by")
                        builder.b { builder.mkp.yield(env.ShelvedChangeOwner) }
                    }
                }
                def files = env.ShelvedChangeDesc.tokenize("\r\n")
                for (int k = 0; k < files.size(); k++) {
                    if (k == MAX_NUM_OF_CHANGELIST_FILES ) {
                        builder.tr { builder.td { builder.mkp.yield("... ${files.size()} files in this Change  ") } }
                        break
                    }

                    def file = files[k]
                    builder.tr {
                        builder.td {
                            builder.mkp.yield("${file}")
                        }
                    }
                }
            }
        }
         if (currentBuild.changeSets.size()) {
            builder.table {
                if (currentBuild.changeSets.size()) {
                    builder.td(class: "changes_header") { b { builder.mkp.yield("Changes") } }
                    def changeLogSets = currentBuild.changeSets
                    def processedGit = [:]
                    for (int i = 0; i < changeLogSets.size(); i++) {
                        def num_of_changes = 0
                        def entries = changeLogSets[i].items
                        if(ScmEnvList != null && ScmEnvList.size()>0){
                            recurseGitURL(builder,ScmEnvList, entries[(entries.size()-1)].id,processedGit)
                        }
                        // for (int j = (entries.length-1); j >= 0; j--) {
                        for(int j =0; j < entries.length; j++){
                            if (num_of_changes >= MAX_NUM_OF_CHANGELISTS ) {
                                builder.tr {
                                    builder.td { builder.mkp.yield("Other changelists are omited...") }
                                }
                                break
                            }
                            num_of_changes += 1
                            def entry = entries[j]
                            def message = ""
                            if (entry.msgAnnotated) {
                                message = entry.msg
                                message = message.replaceAll(~/(?i)(<BR>|&nbsp;|\s)/, " ")
                                if (message.length() > CHANGELIST_DESC_LENGTH_MAX ) {
                                    message = message.substring(0, CHANGELIST_DESC_LENGTH_MAX + 1)+"..."
                                }
                            }
                            builder.tr {
                                builder.td(class: "changes_since_previous") {
                                    builder.mkp.yield("Revision")
                                    builder.b { 
                                        builder.a(href: "${env.BUILD_URL}changes#${entry.revision}") {
                                            builder.mkp.yield(entry.revision) 
                                        }
                                    }
                                    builder.br { }
                                    builder.span {
                                        builder.mkp.yield("by")
                                        builder.b { builder.mkp.yield(entry.author) }
                                    }
                                    builder.span {
                                        builder.mkp.yield(entry.msg)
                                    }
                                }
                            }

                            // def files = new ArrayList(entry.affectedFiles)
                            def files = entries[j].paths
                            for (int k = 0; k < files.size(); k++) {
                                if (k == MAX_NUM_OF_CHANGELIST_FILES ) {
                                    builder.tr { builder.td { builder.mkp.yield("... ${files.size()} files in this Change  ") } }
                                    break
                                }

                                def file = files[k]
                                builder.tr {
                                    builder.td {
                                        builder.mkp.yield("${file.action}: ${file.value}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @NonCPS
    static void generateChangeSetReport(builder, env, currentBuild, ScmEnvList=[]) {
        builder.br {}
        if (env.P4Unshelved){
            builder.table {
                builder.td(class: "shelved_change_header") { b { builder.mkp.yield("Shelved Change") } }
                builder.tr {
                    builder.td(class: "shelved_change_revision") {
                        builder.mkp.yield("Revision")
                        builder.b { builder.mkp.yield(env.ShelvedRevision) }
                    }
                    builder.span {
                        builder.mkp.yield("Unshelved by")
                        builder.b { builder.mkp.yield(env.ShelvedChangeOwner) }
                    }
                }
                def files = env.ShelvedChangeDesc.tokenize("\r\n")
                for (int k = 0; k < files.size(); k++) {
                    if (k == MAX_NUM_OF_CHANGELIST_FILES ) {
                        builder.tr { builder.td { builder.mkp.yield("... ${files.size()} files in this Change  ") } }
                        break
                    }

                    def file = files[k]
                    builder.tr {
                        builder.td {
                            builder.mkp.yield("${file}")
                        }
                    }
                }
            }
        }
        if (currentBuild.changeSets.size()) {
            builder.table {
                if (currentBuild.changeSets.size()) {
                    builder.td(class: "changes_header") { b { builder.mkp.yield("Changes") } }
                    def changeLogSets = currentBuild.changeSets
                    def processedGit = [:]
                    for (int i = 0; i < changeLogSets.size(); i++) {
                        def num_of_changes = 0
                        def entries = changeLogSets[i].items
                        if(ScmEnvList != null && ScmEnvList.size()>0){
                            recurseGitURL(builder,ScmEnvList, entries[(entries.size()-1)].id,processedGit)
                        }
                        for (int j = (entries.length-1); j >= 0; j--) {
                            if (num_of_changes >= MAX_NUM_OF_CHANGELISTS ) {
                                builder.tr {
                                    builder.td { builder.mkp.yield("Other changelists are omited...") }
                                }
                                break
                            }
                            num_of_changes += 1
                            def entry = entries[j]
                            def message = ""
                            if (entry.msgAnnotated) {
                                message = entry.msgAnnotated
                                message = message.replaceAll(~/(?i)(<BR>|&nbsp;|\s)/, " ")
                                if (message.length() > CHANGELIST_DESC_LENGTH_MAX ) {
                                    message = message.substring(0, CHANGELIST_DESC_LENGTH_MAX + 1)+"..."
                                }
                            }
                            builder.tr {
                                builder.td(class: "changes_since_previous") {
                                    builder.mkp.yield("Revision")
                                    builder.b { 
                                        builder.a(href: "${env.BUILD_URL}changes#${entry.id}") {
                                            builder.mkp.yield(entry.id) 
                                        }
                                    }
                                    builder.br { }
                                    builder.span {
                                        builder.mkp.yield("by")
                                        builder.b { builder.mkp.yield(entry.author) }
                                    }
                                    builder.span {
                                        builder.mkp.yield(message)
                                    }
                                }
                            }

                            // def files = new ArrayList(entry.affectedFiles)
                            def files = entries[j].paths
                            for (int k = 0; k < files.size(); k++) {
                                if (k == MAX_NUM_OF_CHANGELIST_FILES ) {
                                    builder.tr { builder.td { builder.mkp.yield("... ${files.size()} files in this Change  ") } }
                                    break
                                }

                                def file = files[k]
                                builder.tr {
                                    builder.td {
                                        builder.mkp.yield("${file.editType.name}: ${file.path}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    @NonCPS
    static void generateBFAReport(builder, env, currentBuild) {
        def errorOutput = []
        def errors = []

        boolean success = currentBuild.currentResult.toUpperCase().equals("SUCCESS")
        if (!success) {
            currentBuild.getRawBuild().getLog(2500).each(){
                line -> errors.add(line)
            }
            errorOutput.add(errors)

            builder.table {
                builder.tr {
                    builder.td(class: "error_header", colspan: "4") {
                        builder.b { builder.mkp.yield("ERROR OUTPUT") }
                    }
                }

                def bfa = currentBuild.getRawBuild().getAction(com.sonyericsson.jenkins.plugins.bfa.model.FailureCauseBuildAction)?.getFoundFailureCauses()
                if (bfa != null && !bfa.isEmpty()) {
                    showBFAOutput(builder, bfa, env)
                } else {
                    errorOutput.each() {
                        proj -> analyzeErrorOutput(builder, proj)
                    }
                }
            }

            builder.br { }
        }
    }

    @NonCPS
    static void generateStepsReport(builder, currentBuild) {
        def runExt = RunExt.createNew(currentBuild.getRawBuild())
        def stages = runExt.getStages()

        if (!stages) {
            return
        }

        builder.table {
            builder.tr {
                builder.td(class: "build_steps_header", colspan: "3") {
                    builder.b("BUILD STAGES")
                }
            }
            stages.each { stage ->
                // Skip Declarative steps.
                if (!stage.getName().contains("Declarative: ") && (stage.getDurationMillis() >= 200)) {
                    renderBasicStage(builder, stage)
                }
            }
        }
    }

    @NonCPS
    private static void renderBasicStage(builder, stage) {
        builder.tr(class: "build_step") {
            builder.td {
                builder.b(stage.getName())
            }
            builder.td(class: "stage_${stage.getStatus()}") {
                builder.b(stage.getStatus())
            }
            builder.td {
                builder.b(formatDuration(stage.getDurationMillis()))
            }
        }
    }

    @NonCPS
    private static formatDuration(millis) {
        String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
            TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
            TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1))
        return hms
    }

    @NonCPS
    static analyzeErrorOutput(builder, errorOutput) {
        builder.tr { builder.td { builder.br { } } }
        errorOutput.each(){ line ->
            if (line.toLowerCase() ==~ PATTERN && !line.toLowerCase().contains("skipped due to earlier failure(s)")) {
                // to skip color coding for none-bfa captured log, as it was too much, and meaning less.
                builder.tr { builder.td { builder.pre(class: "buildlog"){builder.mkp.yield(formatOutput(line.trim()))} } }
            }
        }
    }

    @NonCPS
    static void showBFAOutput(builder, bfa, env) {
        def failedBuild = null
        def sortedKey = []
        def bfaMap = [:]
        bfa.each { failure ->
            sortedKey.add(failure.name)
            bfaMap[failure.name] = failure
        }
        sortedKey = sortedKey.sort()
        sortedKey.each{ k ->
            builder.tr {
                builder.td(class: "failureName") { builder.b { builder.mkp.yield(bfaMap[k].name)}}
            }
            if(env.PrioritizedEmailGroupFilter?.contains(k)){
                PRIORITIZED_EMAIL_GROUP = env.PrioritizedEmailGroup
            }
            if(env.RestartNodeByFailure?.contains(k)){
                TO_RESTART_NODE = env.RestartNodeByFailure
            }
            bfaMap[k].indications.each { ind ->
                builder.tr {
                    builder.td { formatErrorLine(builder, ind.getMatchingString()) }
                }
            }
        }
    }
    @NonCPS 
    static String formatErrorLine(builder, rawOutput) {
        rawOutput.tokenize("\r\n").each(){ line ->
            if (line.toLowerCase() ==~ PATTERN && !line.toLowerCase().contains("skipped due to earlier failure(s)")) {
                builder.pre(class: "failureLine"){ builder.mkp.yield(formatOutput(line.trim())) } 
            }else{
                 builder.pre(class: "buildlog"){builder.mkp.yield(formatOutput(line.trim()))}
            }
        }
    }
    @NonCPS
    static String formatOutput(rawOutput) {
        String formattedOutput = rawOutput
        formattedOutput = formattedOutput.replaceAll('\'','`')
        return formattedOutput
    }
    @NonCPS
    static void generateUPushReport(builder, env, currentBuild, manifestVersion=[:] ){
        builder.div{builder.mkp.yield("${env.BuildId} 版本推送")}
        builder.div{builder.mkp.yield("版本号：")}
        def ServerVersion = ""
        def ClientVersion = ""
        def ClientVersionHeader = ""
        def ClientPatch = "有"
        def ServerUpdate = "无"
        if(env.BuildServer == "true"){
            ServerVersion = env.BuildId
            ServerUpdate = "有"
        }
        if(env.JustBuildAB != "true" && env.BuildClient == "true"){
            ClientVersion = manifestVersion.ClientVersion
            ClientVersionHeader = """"ClientVersion": ${ClientVersion}","""
        }
        
        if(env.JustBuildAB != "true" && env.BuildClient != "true"){
            ClientPatch ="无"
        }
        builder.div{
            builder.pre{
                builder.mkp.yield("""
${ClientVersionHeader}
"ServerVersion": "${ServerVersion}",
"WindowsBundleVersion": "${manifestVersion.WindowsBundleVersion}", 
"iOSBundleVersion": "${manifestVersion.iOSBundleVersion}",  
"AndroidBundleVersion": "${manifestVersion.AndroidBundleVersion}" """)
            }
            builder.br{}
            
        }
        builder.table {
            builder.tr {
                builder.th(class: "left") {
                    builder.mkp.yield("更新模块")
                }
                builder.th(class: "right") {
                    builder.mkp.yield("更新")
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("客户端整包")
                }
                builder.td{
                    builder.mkp.yield("客户端版本: ${ClientVersion}")
                    builder.br{}
                    builder.mkp.yield("服务器版本: ${ServerVersion}")
                }
            }
            builder.tr (class: "even"){
                builder.td {
                    builder.mkp.yield("客户端patch")
                }
                builder.td{
                    builder.mkp.yield(ClientPatch)
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("配置文件")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "even"){
                builder.td {
                    builder.mkp.yield("服务器端更新")
                }
                builder.td{
                    builder.mkp.yield(ServerUpdate)
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("GameSever")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "even"){
                builder.td {
                    builder.mkp.yield("CenterServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("FriendServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "even"){
                builder.td {
                    builder.mkp.yield("GMServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("TeamServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "even"){
                builder.td {
                    builder.mkp.yield("BattleServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("BattleVerifyServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "even"){
                builder.td {
                    builder.mkp.yield("LoginQueueServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("BillingServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "even"){
                builder.td {
                    builder.mkp.yield("LoginAgentServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("SuggestFriendServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "even"){
                builder.td {
                    builder.mkp.yield("MatchServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
            builder.tr (class: "odd"){
                builder.td {
                    builder.mkp.yield("CrossServiceServer")
                }
                builder.td{
                    builder.mkp.yield("")
                }
            }
        }
    }
}