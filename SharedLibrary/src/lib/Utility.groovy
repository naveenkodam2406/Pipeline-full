package lib

import com.cloudbees.plugins.credentials.*
import hudson.scm.SCM
import jenkins.model.Jenkins
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import java.text.SimpleDateFormat


class Utility implements Serializable {
    static credentials = CredentialsProvider.lookupCredentials(
            common.StandardUsernameCredentials.class,
            Jenkins.instance,
            null,
            null
        )
    static GetcredentialByDescription(desc) {
        for (credential in credentials ){
            if( credential.getDescription().matches(desc) ){
                return credential.getId()
            }
        }
    }
    static CleanMasterWorkSpace(script){
        try{
            script.node("master"){
                script.dir(script.env.WORKSPACE) {
                    script.deleteDir()
                }
                script.dir("${script.env.WORKSPACE}@libs") {
                    script.deleteDir()
                }
                script.dir("${script.env.WORKSPACE}@script") {
                    script.deleteDir()
                }
            }
        }
        catch (exception)
        {
            println exception
        }
    }
    static CleanParallelJobWorkSpace(script){
        try{
            script.dir(script.env.WORKSPACE) {
                script.deleteDir()
            }
            script.dir("${script.env.WORKSPACE}@tmp") {
                script.deleteDir()
            }
        }
        catch (exception)
        {
            println exception
        }
    }
    static CleanJobWorkSpace(script){
        try{
            script.dir(script.env.WORKSPACE) {
                script.deleteDir()
            }
            script.dir("${script.env.WORKSPACE}@tmp") {
                script.deleteDir()
            }
            script.node("master"){
                script.dir("${script.env.WORKSPACE}@libs") {
                    script.deleteDir()
                }
                script.dir("${script.env.WORKSPACE}@script") {
                    script.deleteDir()
                }
            }
        }
        catch (exception)
        {
            println exception
        }
    }

    //According to this 
    //https://github.com/TYPO3-infrastructure/jenkins-pipeline-global-library-chefci/blob/master/src/org/typo3/chefci/helpers/JenkinsHelper.groovy
    @NonCPS
    static  createTempLocation(script, String path) {
        String tmpDir = script.pwd tmp: true
        return tmpDir + File.separator + new File(path).getName()
    }

    static CopyGlobalLibraryScript(script, String srcPath, String destPath = null) {
        srcPath = Utility.OS(script).Path(srcPath)
        destPath = destPath ?: createTempLocation(script,srcPath)
        destPath = Utility.OS(script).Path(destPath)
        script.writeFile file: destPath, text: script.libraryResource(srcPath), encoding:"utf-8"
        script.echo "CopyGlobalLibraryScript: copied ${srcPath} to ${destPath}"
        return destPath
    }
    @NonCPS
    static SetEnvParams(script, params){
        params.each{key, value ->l:{
            script.env[key] = value
        }}
    }

    static enum Filter {
        FilterPathImpl ("FilterPathImpl", "path"),
        FilterUserImpl ("FilterUserImpl", "user"),
        FilterViewMaskImpl ("FilterViewMaskImpl","viewMask"),
        FilterPerChangeImpl ("FilterPerChangeImpl","perChange")
        private final String className
        private final String keyName
        private Filter(final String className, final String keyName){
            this.className = className
            this.keyName = keyName
        }
        public String getClassName(){
            return className
        }
        public String getKeyName(){
            return keyName
        }
    }

    static enum SCMClass {
        GitScm("GitSCM"),
        MultiSCM("MultiSCM"),
        SvnSCM("SubversionSCM"),

        StreamWorkspaceImpl("StreamWorkspaceImpl"),
        ManualWorkspaceImpl("ManualWorkspaceImpl"),
        PerforceScm("PerforceScm"),

        private final String className
        private SCMClass(final String className){
            this.className = className
        }
        public String getClassName(){
            return className
        }
    }

    static enum Populate {
        SyncOnlyImpl  (  [ $class: "SyncOnlyImpl",    quiet: true, force: false,  modtime: false, have: true,pin: ""]),
        CheckOnlyImpl (  [ $class: "CheckOnlyImpl",   quiet: true, pin: ""] ),
        FlushOnlyImpl (  [ $class: "FlushOnlyImpl",   quiet: true, pin: ""] ),
        AutoCleanImpl (  [ $class: "AutoCleanImpl",   quiet: true, replace: true, delete:true] )
        private final Map populateImpl
        private Populate(final Map populateImpl){
            this.populateImpl = populateImpl
        }
        public Map getPopulateImpl(){
            return populateImpl
        }
    }

    static OS(script){
        if(script.isUnix()){
            return Utility.MobilePlatform.UNIX
        }else{
            return Utility.MobilePlatform.WINDOWS
        }
    }

    static enum MobilePlatform{
        UNIX ("sh","sh","#"),
        WINDOWS ("powershell","bat","REM"),

        private final String Shell
        private final String Terminal
        private final String CommentSymbol
        String Path(String path){
            if(this.Terminal == "sh"){
                return path.replace("\\","/")
            }
            else if(this.Terminal == "bat"){
                return path.replace("/","\\")
            }
        }
        String Escaping(String txt){
            if(this.Terminal == "sh"){
                //.replace("#","\\#") for this one ,hash/sharp(#) sign is a comment sign in bash
                return txt.replace("#","\\#")
            }
            else if(this.Terminal == "bat"){
                return txt
            }
        }
        private MobilePlatform(final String shell,final String terminal,final String commentSymbol){
            this.Shell = shell
            this.Terminal = terminal
            this.CommentSymbol = commentSymbol
        }
    }
    class Svn {
        public static final String Update = "svn update "
        public static final String Cleanup = "svn cleanup "
        public static final String Revert = "svn revert "
        static String Checkout (URL){
            return "svn checkout ${URL} "
        }
        class SvnOpt {
            public static final String username = " --username "
            public static final String password = " --password "
            public static final String depthInfinity = " --depth infinity "
            public static final String depthEmpty = " --depth empty "
            public static final String depthFiles = " --depth files "
            public static final String depthImmediates = " --depth immediates "
            public static final String nonInteractive = " --non-interactive "
            public static final String quiet = " --quiet "
            public static final String ignoreExternals = " --ignore-externals "
            public static final String force = " --force "
            public static final String recursive = " --recursive "
        }
    }

    static enum Git{
        GlobalConfQuotepath ("git config --global core.quotepath off "), // for displaying Chinese path
        Add ("""git add -A """),        
        Commit ("""git commit -m "{DESC}" """),
        Branch ("""git branch """),
        Push ("""git push """),
        CurrentBranch ("""git rev-parse --abbrev-ref HEAD"""),
        LogM1 ("""git log -1 """),
        OriginURL ("""git config --get remote.origin.url"""),
        ResetHard ("git reset --hard FETCH_HEAD"),
        ResetHardNoneHEAD ("git reset --hard"),
        PullRebase ("git pull --rebase"),
        Pull ("git pull "),
        CheckOutWithOpts ("git checkout "),
        CheckOutCurrent ("git checkout ."),
        SwitchWithOpts ("git switch "),
        CleanDf ("git clean -df"),
        StagedFiles ("git diff --name-only --cached"),
        SubmoduleForeach ("git submodule foreach "),
        SubmoduleCheckOutCurrent ("git submodule foreach git checkout ."),
        SubmoduleCleanDf ("git submodule foreach git clean -df"),
        SubmoduleUpdatePrune ("git submodule foreach git remote update origin --prune"),
        SubmoduleCheckout ("git submodule foreach git checkout "), // to append with branchName 
        SubmodulePull ("git submodule foreach git pull"),
        SubmodulePullRebase ("git submodule foreach git pull --rebase"),
        Status ("""git status """),
        SubmoduleStatus ("git submodule foreach git status"),
        SubmoduleCurrentBranch ("""git submodule foreach git rev-parse --abbrev-ref HEAD"""),
        SubmoduleLogM1 ("""git submodule foreach git log -1 """),
        SubmoduleOriginURL ("""git submodule foreach git config --get remote.origin.url"""),
        private final String CMD
        private Git(final String cmd){
            this.CMD = cmd
        }
    }
    @NonCPS
    static UnifyGITURLForSubmodule(script, ROOT_GIT_URL, SUBMODULE_URL ){
        def HTTP_REGEX = /http:\/\/\d+.\d+.\d+.\d+:{0,1}\d{0,}/
        def SSH_REGEX = /(ssh:\/\/){0,1}git@\d+.\d+.\d+.\d+:\d{0,}/
        def IP_REGEX= /\d+.\d+.\d+.\d+/
        def subm_IPFound = (SUBMODULE_URL =~ IP_REGEX)
        def root_IPFound = (ROOT_GIT_URL =~ IP_REGEX)
        def submIP = ""
        def rootIP = ""
        if(subm_IPFound.find()){
            submIP = subm_IPFound.group()
        }
        if(root_IPFound.find()){
            rootIP = root_IPFound.group()
        }
        if(rootIP != "" && submIP != "" && rootIP != submIP){
            ROOT_GIT_URL = ROOT_GIT_URL.replace(rootIP, submIP) // to make root ip replace with submodule ip, 
            // since we only need protocal, server should be align with the submodule's conf all the time
        }

        def isHTTP = ( ROOT_GIT_URL =~ HTTP_REGEX )
        def isSSH = ( ROOT_GIT_URL =~ SSH_REGEX )
        def host = ""
        if(isHTTP.find()){
            host = isHTTP.group()
        }
        else if(isSSH.find()){
            host = isSSH.group()
        }
        if(host == ""){
            script.error("GIT host format is incorrect.")
        }
        if(ROOT_GIT_URL.substring(0,4) != SUBMODULE_URL.substring(0,4) ){ // needs to align with the root format
            def matcherHttp = ( SUBMODULE_URL =~ HTTP_REGEX ) 
            def matcherSSH = ( SUBMODULE_URL =~ SSH_REGEX )
            if(matcherHttp.find()){
                SUBMODULE_URL = SUBMODULE_URL.replace(matcherHttp.group(),host )
            }
            else if(matcherSSH.find()){
                def suffix = SUBMODULE_URL.replace(matcherSSH.group(), "")
                if(suffix.startsWith("/")){
                    SUBMODULE_URL = host + suffix
                }
                else{
                    SUBMODULE_URL  = host + "/" +suffix
                }
            }
        }
        return SUBMODULE_URL
    }
    @NonCPS
    static RegExFind(str, regExStr){
        def matcherArtifact = (str =~ regExStr )
        if(matcherArtifact.find()){
            return matcherArtifact.group()
        }else{
            return null
        }
        
    }
    @NonCPS
    static GetBuildInfo(conf=[:]){
        def m_buildNo = conf.buildNo
        def m_build
        def m_artifacts=[]
        if(!m_buildNo){
            m_build = Jenkins.getInstance().getItem(conf.projectName).getLastSuccessfulBuild()
            m_buildNo = m_build.number
        }
        else{
            m_build = Jenkins.getInstance().getItem(conf.projectName).getBuildByNumber(m_buildNo as int)
        }
        if(m_build == null) return null
        for(ar in m_build.artifacts){
            def _a = [:]
            _a["relativePath"] = ar.relativePath
            _a["fileName"] = ar.fileName
            _a["displayPath"] = ar.displayPath
            m_artifacts.add(_a)
        }
        return [artifacts:m_artifacts, buildNumber:"${m_build.number}", url:m_build.url]
    }
    static ObjToJsonStr(jsonObj, prettyOut = false){
        if(prettyOut){
            return JsonOutput.prettyPrint(JsonOutput.toJson(jsonObj))
        }else{
            return JsonOutput.toJson(jsonObj)
        }
    }
    @NonCPS
    static JsonStrToObj(jsonStr){
        return new JsonSlurperClassic().parseText(jsonStr)
    }
    static processedScm = [:]

 @NonCPS
    static SetSvnDisplayName(SvnSCMs, currentBuild, SCMList=[],useCommitId = false){
        if(SCMList==[]) processedScm = [:]
        if(SvnSCMs instanceof List){
            SvnSCMs.each{SvnSCM ->l:{
                SCMList = Utility.SetSvnDisplayName(SvnSCM, currentBuild, SCMList, useCommitId)
            }}
        }else{
            //   currentBuild.displayName+="." +SvnSCMs.SVN_REVISION
            // if(!SvnSCMs.Submodules){           
                // def repoName = Utility.RegExFind(SvnSCMs.ScmEnv.SVN_URL, "\\w+.git")
                // if(repoName == null){
                //     repoName = SvnSCMs.ScmEnv.GIT_CHECKOUT_DIR.replace("Submodules/","")
                // }
                // if(processedScm.containsKey(repoName)){
                //     return SCMList
                // }
                // processedScm[repoName]="" // so we know it is processed
                // currentBuild.displayName += " " + repoName.substring(0,1)
                // if(useCommitId){
                //     currentBuild.displayName += "." + SvnSCMs.ScmEnv.SVN_COMMIT.substring(0,7)
                // }else{
                //     currentBuild.displayName += "." + SvnSCMs.ScmEnv.SVN_URL
                // }
                //GitSCMs.ScmEnv.GIT_COMMIT.substring(0,7)
                // SCMList.add(SvnSCMs.ScmEnv)
                // return SCMList
            // }else{
            //     SCMList = Utility.SetSvnDisplayName(SvnSCMs, currentBuild, SCMList, useCommitId)
            // }
        }
    }
    @NonCPS
    static SetDisplayName(GitSCMs, currentBuild, SCMList=[],useCommitId = false){
        if(SCMList==[]) processedScm = [:]
        if(GitSCMs instanceof List){
            GitSCMs.each{GitSCM ->l:{
                SCMList = Utility.SetDisplayName(GitSCM, currentBuild, SCMList, useCommitId)
            }}
        }else{
            if(!GitSCMs.Submodules){
                
                def repoName = Utility.RegExFind(GitSCMs.ScmEnv.GIT_URL, "\\w+.git")
                if(repoName == null){
                    repoName = GitSCMs.ScmEnv.GIT_CHECKOUT_DIR.replace("Submodules/","")
                }
                if(processedScm.containsKey(repoName)){
                    return SCMList
                }
                processedScm[repoName]="" // so we know it is processed
                currentBuild.displayName += " " + repoName.substring(0,1)
                if(useCommitId){
                    currentBuild.displayName += "." + GitSCMs.ScmEnv.GIT_COMMIT.substring(0,7)
                }else{
                    currentBuild.displayName += "." + GitSCMs.ScmEnv.GIT_LOCAL_BRANCH
                }
                //GitSCMs.ScmEnv.GIT_COMMIT.substring(0,7)
                SCMList.add(GitSCMs.ScmEnv)
                return SCMList
            }else{
                SCMList = Utility.SetDisplayName(GitSCMs.Submodules, currentBuild, SCMList, useCommitId)
            }
        }
    }
    @NonCPS
    static UpdateJobStatusOnELK(script){
        def runEx = script.currentBuild.getRawBuild()
        def status = ""
        if(runEx.result) {status = runEx.result}
        else if(runEx.building) {status = "RUNNING"}
        script.elasticQuery("addToElastic", """--url http://192.168.5.100:9200/jobstatus/_doc --data "{'build_url':'${script.env.RUN_DISPLAY_URL}','job_name':'${script.env.JOB_NAME}','build_no':'${script.env.BUILD_NUMBER}','result':'${status}'}" --id ${script.env.JOB_NAME} """)
    }
    @NonCPS
    static AddJobResultOnELK(script){
        try{
            if(script.env.ELKURL){
                if(script.env.SCMStr == null) script.env.SCMStr = ""
                script.elasticQuery("addToElastic", """--url ${script.env.ELKURL} --data "{'opt':'job','build_url':'${script.env.RUN_DISPLAY_URL}','job_name':'${script.env.JOB_NAME}','build_no':'${script.env.BUILD_NUMBER}', ${script.env.SCMStr} 'result':'${script.currentBuild.result}'}" """)
            }
        }
        catch(Exception ex){
            print("Failed when doing addToElastic, but not fail this build.")
            print ex
        }
    }
    @NonCPS
    static MarkStageAsSkipped(String StageName){
        Utils.markStageSkippedForConditional(StageName)
    }
    @NonCPS
    static GetCurrentDate(style="yyyy.MM.dd_HH.mm"){
        def date = new Date()
        //def sdf = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss")
        def sdf = new SimpleDateFormat(style)
        return sdf.format(date)
    }
    @NonCPS
    static ReplaceSlash(String str){
        return str.replace("\"","_").replace("/","_")
    }
    static String PasswordEncoding(String str){
        //https://www.degraeve.com/reference/urlencoding.php
        return str.replace("%","%25").replace("#","%23").replace("'","%27")
        .replace("!","%21").replace("\$","%24").replace("?","%3F")
        .replace("=","%3D").replace("\\","%5C")
    }
    static Boolean IsJobRunning(String jobName){
        def job = Jenkins.getInstance().getItemByFullName(jobName)
        return ( job.isBuilding() || job.isInQueue() )
    }
}