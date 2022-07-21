//https://jenkinsci.github.io/dingtalk-plugin/
package lib.dingding
import com.cloudbees.groovy.cps.NonCPS
import com.cloudbees.workflow.rest.external.RunExt
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

class DingMSGGenerator {
    //https://jenkinsci.github.io/dingtalk-plugin/
    static final int MAX_NUM_OF_CHANGELISTS = 10
    static final int MAX_NUM_OF_CHANGELIST_FILES = 3
    static final int CHANGELIST_DESC_LENGTH_MAX = 40
    static final int ERROR_MAX_LENGTH = 2000
    static final int ERROR_EACH_IND_LENGTH = 500
    static final int MIN_REQUIRED_STEP_DURATION = 1200
    static String PRIORITIZED_EMAIL_GROUP = ""
    static boolean TO_RESTART_NODE = false

    @NonCPS
    static GenerateSnipMD(env, currentBuild, status=""){
        // ERROR: 钉钉机器人发生错误：{"errcode":460101,"errmsg":"message too long, exceed 20000 bytes"}
        // the above line is for reference
        def timestamp = currentBuild.getRawBuild().getTimestamp().format('dd/MM/yyyy HH:mm:ss')
        def mdBody=[]
        if(status == "" || status == null) status = currentBuild.currentResult
        mdBody.add(MDFormat.Preformatted."${status}".MDText)
        mdBody.add(MDFormat.Preformatted.BOUNDARYLINE.MDText)
        def durationTime = "${currentBuild.durationString}".replace("and counting","").trim()
        mdBody.add(MDFormat.Preformatted.H_FIVE.MDText + """[${env.JOB_NAME} ${env.BUILD_NUMBER}](${env.RUN_DISPLAY_URL})""")
        mdBody.add(MDFormat.Preformatted.H_SIX.MDText + "${env.BUILD_DISPLAY_NAME}")
        if(currentBuild.description != null){
            currentBuild.description.split("<br/>").each{
                if(!it.contains("<img")){
                    def tmpstr = "${it}".replace("\\","&#92;") // escaping backslash for markdown
                    mdBody.add(MDFormat.Preformatted.H_SIX.MDText + tmpstr)
                }
            }
        }
        
        

        def jobDesc = MDFormat.Preformatted.H_SIX.MDText + """${durationTime}, ${timestamp}"""

        def uid = GetBuildUser(currentBuild)
        if(uid){
            jobDesc +=" by ${uid}"
        }
        mdBody.add(jobDesc) 
        mdBody.addAll(GenerateBFAReport( env, currentBuild))
        mdBody.addAll(ArtifactReport(env, currentBuild))
        mdBody.addAll(GenerateStepsReport(currentBuild))
        switch(currentBuild.changeSets.toString()) {
                    case ~/.*SubversionChangeLogSet.*/:
                        mdBody.addAll(GetSvnChangeSetReport(currentBuild))
                    break;
                    case ~/.*GitChangeSet.*/:
                        mdBody.addAll(GetChangeSetReport(currentBuild))
                    break;
                }
        // mdBody.addAll(GetChangeSetReport(currentBuild))

        return mdBody
    }
    @NonCPS
    static GetSvnChangeSetReport(currentBuild){
        def changeSetReport = []
        if (currentBuild.changeSets.size()) {
            
            changeSetReport.add(MDFormat.Preformatted.BOUNDARYLINE.MDText)
            int totalcount = 0
            changeSetReport.add(MDFormat.Preformatted.CHANGESET.MDText)
            def changeLogSets = currentBuild.changeSets
            for (int i = 0; i < changeLogSets.size(); i++) {
                def entries = changeLogSets[i].items
                // for (int j = entries.length-1; j >=0 && totalcount < MAX_NUM_OF_CHANGELISTS ; j--) {
                for(int j=0;j<entries.length && totalcount < MAX_NUM_OF_CHANGELISTS ; j++){ 
                    def entry = entries[j]
                    // def rid = "${entry.revision}".substring(0,7)
                    def rid = "${entry.revision}"

                    def message = ""
                    if (entry.msg) {
                        message = entry.msg
                        message = message.replaceAll(~/(?i)(<BR>|&nbsp;|\s)/, " ")
                        if (message.length() > CHANGELIST_DESC_LENGTH_MAX) {
                            message = message.substring(0, CHANGELIST_DESC_LENGTH_MAX + 1)+"..."
                        }
                    }
                    def checkinTime = new Date(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS Z").parse(entry.date.replace("Z"," UTC")).getTime()).format('dd/MM/yyyy HH:mm:ss')
                    // def checkinTime = entry.date
                    
                    changeSetReport.add(MDFormat.Preformatted.REFSYMBOL.MDText + "Ver.${rid} by ${entry.author.toString()} at ${checkinTime}")
                    changeSetReport.add(MDFormat.Preformatted.H_SIX.MDText + "${message}")
                    totalcount++
                    if(totalcount >= MAX_NUM_OF_CHANGELISTS){
                        changeSetReport.add(MDFormat.Preformatted.REFSYMBOL.MDText + "...")
                    }
                    // def files = new ArrayList(entry.affectedFiles)
                    // for (int k = 0; k < files.size(); k++) {
                    //     if (k == MAX_NUM_OF_CHANGELIST_FILES) {
                    //         changeSetReport.add(MDFormat.Preformatted.REFSYMBOL.MDText + "... ${files.size()} files in this Change")
                    //         break
                    //     }
                    //     def file = files[k]
                    //     changeSetReport.add(MDFormat.Preformatted.H_FIVE.MDText + "${file.editType.name}: ${file.path}")
                    // }
                }
            }
        }
        return changeSetReport
    }

    @NonCPS
    static GetChangeSetReport(currentBuild){
        def changeSetReport = []
        if (currentBuild.changeSets.size()) {
            
            changeSetReport.add(MDFormat.Preformatted.BOUNDARYLINE.MDText)
            int totalcount = 0
            changeSetReport.add(MDFormat.Preformatted.CHANGESET.MDText)
            def changeLogSets = currentBuild.changeSets
            for (int i = 0; i < changeLogSets.size(); i++) {
                def entries = changeLogSets[i].items
                for (int j = entries.length-1; j >=0 && totalcount < MAX_NUM_OF_CHANGELISTS ; j--) {
                    def entry = entries[j]
                    def rid = "${entry.id}".substring(0,7)

                    def message = ""
                    if (entry.msgAnnotated) {
                        message = entry.msgAnnotated
                        message = message.replaceAll(~/(?i)(<BR>|&nbsp;|\s)/, " ")
                        if (message.length() > CHANGELIST_DESC_LENGTH_MAX) {
                            message = message.substring(0, CHANGELIST_DESC_LENGTH_MAX + 1)+"..."
                        }
                    }
                    def checkinTime = new Date(entry.timestamp).format('dd/MM/yyyy HH:mm:ss')
                    changeSetReport.add(MDFormat.Preformatted.REFSYMBOL.MDText + "Ver.${rid} by ${entry.author.toString()} at ${checkinTime}")
                    changeSetReport.add(MDFormat.Preformatted.H_SIX.MDText + "${message}")
                    totalcount++
                    if(totalcount >= MAX_NUM_OF_CHANGELISTS){
                        changeSetReport.add(MDFormat.Preformatted.REFSYMBOL.MDText + "...")
                    }
                    // def files = new ArrayList(entry.affectedFiles)
                    // for (int k = 0; k < files.size(); k++) {
                    //     if (k == MAX_NUM_OF_CHANGELIST_FILES) {
                    //         changeSetReport.add(MDFormat.Preformatted.REFSYMBOL.MDText + "... ${files.size()} files in this Change")
                    //         break
                    //     }
                    //     def file = files[k]
                    //     changeSetReport.add(MDFormat.Preformatted.H_FIVE.MDText + "${file.editType.name}: ${file.path}")
                    // }
                }
            }
        }
        return changeSetReport
    }
    @NonCPS
    static GetBuildUser(currentBuild) {
        def build = currentBuild.getRawBuild()
        def BUILD_USER = ""

        try {
             def cause = build.getCause(hudson.model.Cause.UserIdCause.class)
             BUILD_USER = cause.getUserName()
        } catch(Exception ex) {
             println "\n\n-- Build caused by either Multi-Branch Pipeline Scanning -or- Timer i.e. not directly by a logged in user\n";
             BUILD_USER = "Branch_Scan_or_Timer"
        }

        return BUILD_USER
    }
    @NonCPS
    static GenerateStepsReport(currentBuild) {
        def runExt = RunExt.createNew(currentBuild.getRawBuild())
        def stages = runExt.getStages()
        def buildStages = []
        if (!stages) {
            return
        }
        buildStages.add(MDFormat.Preformatted.BOUNDARYLINE.MDText)
        buildStages.add(MDFormat.Preformatted.BUILDSTAGES.MDText)
        stages.each { stage ->
            // Skip Declarative steps.
            if (!stage.getName().contains("Declarative: ") && (stage.getDurationMillis() >= MIN_REQUIRED_STEP_DURATION)) {
                buildStages.add(RenderBasicStage(stage))
            }
        }
        return buildStages
    }
    @NonCPS
    private static RenderBasicStage(stage) {
        def stepText = MDFormat.Preformatted.H_SIX.MDText + stage.getName() + " "
            stepText +=  MDFormat.Preformatted."STEP_${stage.getStatus().toString()}".MDText + " "
            stepText +=  FormatDuration(stage.getDurationMillis()) 
        return  stepText
    }
    @NonCPS
    private static FormatDuration(millis) {
        String hms = String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(millis),
            TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1),
            TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1))
        return hms
    }
    @NonCPS
    private static ArtifactReport(env, currentBuild) {
        def artifactMD = []
        def artifactText =""
        if(currentBuild.getRawBuild().artifacts){
            artifactMD.add(MDFormat.Preformatted.BOUNDARYLINE.MDText)
            artifactMD.add(MDFormat.Preformatted.ARTIFACTS.MDText)
            artifactText += MDFormat.Preformatted.H_SIX.MDText
            currentBuild.getRawBuild().artifacts.each{ artifact -> l:{
                def path = env.BUILD_URL + "artifact/" +artifact.relativePath
                artifactText += """ [${artifact.fileName}](${path}) """
            }}
            artifactMD.add(artifactText)
        }
        return artifactMD
    }
    @NonCPS
    static GenerateBFAReport( env, currentBuild) {
        def errors = []
        def bfaReport =[]
        boolean success = currentBuild.currentResult.toUpperCase().equals("SUCCESS")
        if (!success) {
            def bfa = currentBuild.getRawBuild().getAction(com.sonyericsson.jenkins.plugins.bfa.model.FailureCauseBuildAction)?.getFoundFailureCauses()
            if (bfa != null && !bfa.isEmpty()) {
                bfaReport.add(MDFormat.Preformatted.BOUNDARYLINE.MDText)
                bfaReport.add(MDFormat.Preformatted.ERROROUTPUT.MDText)
                bfaReport.addAll(ShowBFAOutput(bfa, env ))
            }
        }
        return bfaReport
    }
    @NonCPS
    static ShowBFAOutput(bfa,env) {
        def sortedKey = []
        def bfaMap = [:]
        def failureCauseMD = []
        def bfaskip = []
        if(env.SkippedBFA != null){
            bfaskip = env.SkippedBFA.tokenize(",")
        }
        for (int i = 0; i < bfa.size(); i ++){
            def skipped = false
            def failure = bfa[i]
            for (int j = 0; j < bfaskip.size(); j ++){
                if(failure.name == bfaskip[j].trim()){
                    skipped = true
                    continue
                }
            }
            if(skipped) continue
            sortedKey.add(failure.name)
            bfaMap[failure.name] = failure
        }
        sortedKey = sortedKey.sort()
        int ind_Length = 0
        sortedKey.each{ k ->
            failureCauseMD.add(MDFormat.Preformatted.REFSYMBOL.MDText + bfaMap[k].name)
            for (int i = 0; i<bfaMap[k].indications.size() && ERROR_MAX_LENGTH > ind_Length ; i++ ){
                def ind = bfaMap[k].indications[i]
                int each_ind_length = 0
                if(ERROR_MAX_LENGTH < ind_Length) return
                // to truncate timestamp of pipeline log, so we can get more meaningful error logs.
                ind.getMatchingString().replaceAll(/\[\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}.\d{3}Z\]/,"").trim().tokenize("\r\n").each(){ line -> j:{
                    if (!line.toLowerCase().contains("skipped due to earlier failure(s)") && ERROR_EACH_IND_LENGTH > each_ind_length) {
                        int resetLength = ERROR_EACH_IND_LENGTH - each_ind_length
                        def outMsg = formatOutput(line, resetLength)
                        failureCauseMD.add(outMsg)
                        ind_Length += outMsg.length()
                        each_ind_length += outMsg.length()
                    }
                    else{
                        return
                    }
                }}
            }
        }
        return failureCauseMD
    }
    @NonCPS
    static String formatOutput(rawOutput, resetLength) {
        if(resetLength<=0) return "..."
        int lineLen = rawOutput.length()
        if(lineLen > resetLength) lineLen = resetLength
        rawOutput = rawOutput.substring(0,lineLen)
        String formattedOutput = MDFormat.Preformatted.H_SIX.MDText + rawOutput.replaceAll('\'','`').trim()
        return formattedOutput
    }
}