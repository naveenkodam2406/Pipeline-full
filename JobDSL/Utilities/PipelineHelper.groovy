package Utilities

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import Utilities.Parameters
import java.nio.file.Paths
import java.nio.file.Path
import java.util.regex.*


class PipelineHelper {
    static mapMerge(target, source){
        def _target = [:] << target?:[:]
        source.each{k, v ->l:{
          if (v in Map){
            _target[k] in Map ? (_target[k] = mapMerge(target[k],v)) : (_target[k] = v)
          }
          else if (v in List){
            def tempListS = v.collect()
            if(_target[k] in List){
            	def tempListT = _target[k].collect()
              	tempListT.addAll(tempListS)
              	_target[k] = tempListT
              println _target[k]
            }else{
            	_target[k] = tempListS
              println _target[k]
            }
          }
          else{
          	_target[k] = v
          }
        }}
        return _target
    }


    static GenProjJob(jm, String jsConf,gitSCMSource=null){
        def proJson = ProcessImportJson(jm,jsConf)
        def jobMap = [:] << proJson.Jobs
        proJson.remove("Jobs")
        jobMap.each{
            job, conf -> l:{
                def BuildArgs = mapMerge(proJson,conf)
                BuildArgs.CronMap = [:] << (    BuildArgs.Scm   ? [Scm:BuildArgs.Scm]   :   [:] )
                BuildArgs.CronMap       << (    BuildArgs.Cron  ? [Cron:BuildArgs.Cron] :   [:] )
                GenGitJob(jm, BuildArgs, gitSCMSource)
            }
        }
    }

    static GenGitJob(jm,JobMap, gitSCMSource=null){
        jm.with{
            pipelineJob(JobMap.Name){
                PipelineCommentSet(delegate, JobMap)
                definition {
                    cpsScm {
                        lightweight()
                        GitHelper.gitSCM(delegate, gitSCMSource)
                        scriptPath(JobMap.JenkinsScript)
                    }
                }
            }
        }
    }

    static PipelineCommentSet(pipelineJobContext,JobMap){
        pipelineJobContext.with{
            description(JobMap.Desc)
            Parameters.GeneralParamSetup(delegate,JobMap)
            properties {
                if (!JobMap.ConcurrentBuilds){
                    disableConcurrentBuilds()
                }
            }
        }
    }

    static ProcessImportJson(jm,jsConf){
        def jsonSlurper = new JsonSlurper()
        def proJson = jsonSlurper.parseText(jsConf)
        def superProjConf = [:]
        if (proJson.import){
            proJson.import.each{_importedJsConf -> l:{
                superProjConf << jsonSlurper.parseText(jm.jm.readFileInWorkspace(_importedJsConf))
            }}
            proJson.remove("import")
            def _tempAll = superProjConf << proJson
            proJson = _tempAll
        }
        
        return proJson
    }

    static GenTemplateSeedJob(jm, String jsConf, gitScmSource){
        Map proJson = ProcessImportJson(jm, jsConf)
        def jobMap = [:] << proJson.Jobs
        proJson.remove("Jobs")
        jobMap.each{
            k, conf -> l:{
                def projectJson = mapMerge(proJson,conf)
                
                jm.with{
                    job(projectJson.Name){
                        Parameters.jobDSL (delegate)
                        Parameters.GeneralParamSetup(delegate,projectJson)
                        GitHelper.gitSCM(delegate, gitScmSource)
                        steps{
                            dsl {external(projectJson.JenkinsScript)} 
                        }
                    }
                }
            }
        }
    }
}