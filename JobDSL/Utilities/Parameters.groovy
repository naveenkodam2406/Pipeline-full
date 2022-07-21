package Utilities

import java.util.regex.*
import java.nio.file.Paths
import java.nio.file.Path

class Parameters {
    
    static JobSCMTrigger(jobContext,cronMap) {
      jobContext.with { 
        triggers {
            if (cronMap.Scm){
                scm cronMap.Scm
            }
            if (cronMap.Cron){
                cron cronMap.Cron
            }
        }  
      } 
    }
    static DisableJob(jobContext){
        jobContext.with{
            disabled()
        }
    }
    static throttleConcurrentBuilds(jobContext,throttleMap) {
        jobContext.with{
            throttleConcurrentBuilds {
                throttleMap.each{ k,v ->l:{
                    "${k}"(v)
                }}
            }
        }
    }
    static jobDSL(jobContext) {
        jobContext.with{
            wrappers {
                timestamps()
            }
            logRotator {
                daysToKeep 7
            }
            label "master" // jobDSL runs on master all the time
            concurrentBuild false
            properties {
                buildFailureAnalyzer(true)
            }
        }
    }

    static GeneralParamSetup(jobContext, JobMap){
        if(JobMap.GitParams) {
            Parameters.GitParamList(jobContext, JobMap.GitParams)
        }
        if (JobMap.ChoiceParams){
            Parameters.ChoiceParamList(jobContext, JobMap.ChoiceParams)
        }
        if(JobMap.StringParams){
            Parameters.StringParamList(jobContext, JobMap.StringParams)
        }
        if (JobMap.BooleanParams){
            Parameters.BooleanParamList(jobContext, JobMap.BooleanParams)
        }
        if (JobMap.BooleanParamsWithDesc){
            Parameters.BooleanParamListWithDesc(jobContext, JobMap.BooleanParamsWithDesc)
        }
        if(JobMap.FileParams) {
            Parameters.FileParamList(jobContext, JobMap.FileParams)
        }
        if(JobMap.ActiveChoiceParam){
            Parameters.ActiveChoiceParamList(jobContext, JobMap.ActiveChoiceParam)
        }
        if (JobMap.CronMap){
            Parameters.JobSCMTrigger(jobContext, JobMap.CronMap)
        }
        if (JobMap.StageSelector){
            Parameters.BooleanParamList(jobContext, JobMap.StageSelector)
        }
        if(JobMap.AuthenticationToken){
            Parameters.SetAuthenticationToken(jobContext, JobMap.AuthenticationToken)
        }
        if(JobMap.Disabled=="True" || JobMap.Disabled == true){
            Parameters.DisableJob(jobContext)
        }
    }
    // "AuthenticationToken":"danieltest"
    static SetAuthenticationToken(jobContext,authToken){
        jobContext.with{
            authenticationToken(authToken)
        }
    }
    //"BooleanParams":["aaa","bbbb:false","ccc"],
    //"StageSelector":["Kick.Build"]
    // by default true
    static BooleanParamList(jobContext, paramNameValue){
        def reducedMap = [:]
        paramNameValue.each{ _stageName ->l:{
            reducedMap[(_stageName.tokenize(":")[1]?_stageName.tokenize(":")[0]:_stageName)] = _stageName.tokenize(":")[1]?_stageName.tokenize(":")[1].toBoolean():true
        }}
        jobContext.with{
            parameters{
                reducedMap.each{ n,v ->l:{
                    booleanParam(n,v,"")
                }}
            }
        }
    }
    // "ChoiceParams":{"Stream":["aaa","bbb","ccc"]},
    static ChoiceParamList(jobContext, paramNameValue){
        jobContext.with{
            parameters{
                paramNameValue.each{ _choiceName,_choiceList ->l:{
                    if(_choiceList instanceof Map){
                        choiceParam(_choiceName,_choiceList.Items,_choiceList.Desc)
                    }else{
                        choiceParam(_choiceName,_choiceList,"")
                    }
                }}
            }
        }
    }
    //"BooleanParamsWithDesc":{"aaaaa":["","desc"], "bbbbb":["false","desc"],}
    static BooleanParamListWithDesc(jobContext, paramNameValue){
        jobContext.with{
            parameters{
                paramNameValue.each{ booleanParamName, _booleanParamValueDesc ->l:{
                    if (_booleanParamValueDesc.size()>=2){ // for overwritten
                        _booleanParamValueDesc =_booleanParamValueDesc.takeRight(2)
                    }
                    booleanParam(booleanParamName, _booleanParamValueDesc[0]?_booleanParamValueDesc[0].toBoolean():true, _booleanParamValueDesc[1]?:"")
                }}
            }
        }
    }
    static ActiveChoiceParamList(jobContext, paramNameValue){
        jobContext.with{
            parameters{
                paramNameValue.each{ _choiceName,_choiceList ->l:{
                    if(_choiceList instanceof Map){
                        activeChoiceParam(_choiceName) {
                            description(_choiceList.Desc)
                            filterable(_choiceList.Filterable?:false)
                            choiceType(_choiceList.ChoiceType) //SINGLE_SELECT, MULTI_SELECT, CHECKBOX, RADIO
                            groovyScript {
                                script('''
                                import java.io.BufferedReader
                                import java.io.InputStreamReader
                                import java.io.OutputStreamWriter
                                import java.net.URL
                                import java.net.URLConnection
                                import groovy.json.JsonSlurper

                                def choices = ["------":"---PleaseSelect---"]
                                def url = new URL("'''+ _choiceList.Url + '''")
                                def conn = url.openConnection()
                                conn.setDoOutput(true)

                                def reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))
                                def results = new JsonSlurper().parseText(reader.getText());
                                reader.close()

                                results.each { data -> choices[data.'''+ _choiceList.Key + ''']=data.'''+ _choiceList.Value + ''' }
                                return choices.sort()
                                '''.stripIndent().trim())
                                fallbackScript(_choiceList.FallbackScript?:"")
                            }
                        }
                    }
                }}
            }
        }
    }
    //"StringParams":{ "Username":["root","root"],"Password":["","password of Root","Password"] },
    static StringParamList(jobContext, paramNameValue){
        jobContext.with{
            parameters{
                paramNameValue.each{ stringParamName, _stringParamValueDesc ->l:{
                    def tempValueDesc = _stringParamValueDesc
                    if(tempValueDesc.size()>=3){
                        if(_stringParamValueDesc[_stringParamValueDesc.size()-1] =="Password" || _stringParamValueDesc[_stringParamValueDesc.size()-1] =="Text"){
                            tempValueDesc = _stringParamValueDesc.takeRight(3)
                        }
                        else{
                            tempValueDesc = _stringParamValueDesc.takeRight(2)
                        }
                    }
                    if(tempValueDesc.size()<=2){
                        stringParam(stringParamName, tempValueDesc[0]?:"", tempValueDesc[1]?:"")
                    }
                    else if (tempValueDesc[2]=="Password"){
                        simpleParam('hudson.model.PasswordParameterDefinition',stringParamName, tempValueDesc[0]?:"", tempValueDesc[1]?:"")
                    }
                    else if(tempValueDesc[2]=="Text"){
                        textParam(stringParamName, tempValueDesc[0]?:"", tempValueDesc[1]?:"")
                    }
                }}
            }
        }
    }
    static GitParamList(jobContext, paramNameValue){
        jobContext.with{
            parameters{
                paramNameValue.each{_name, kvs ->l:{
                    gitParameterDefinition {
                        name(_name)
                        type(kvs.type?:"PT_BRANCH")
                        description(kvs.description?:"")
                        defaultValue(kvs.defaultValue)
                        branch(kvs.branch?:"")
                        branchFilter(kvs.branchFilter?:"origin/(.*)")
                        tagFilter(kvs.tagFilter?:".*")
                        sortMode(kvs.sortMode?:"NONE")
                        selectedValue(kvs.selectedValue?:"DEFAULT")
                        listSize(kvs.listSize?:"0")
                        useRepository(kvs.useRepository)
                        quickFilterEnabled(kvs.quickFilterEnabled?kvs.quickFilterEnabled.toBoolean():false)
                    }
                }}
            }
        }
    }
    static FileParamList(jobContext, paramNameValue){
        jobContext.with{
            parameters{
                paramNameValue.each{_name, desc ->l:{
                    simpleParam('io.jenkins.plugins.file__parameters.StashedFileParameterDefinition',_name, null,desc)
                }}
            }
        }
    }
}