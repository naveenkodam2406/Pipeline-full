import Utilities.*
import hudson.model.*


Build build = Executor.currentExecutor().currentExecutable
def paramsKV = [:]
/**
*Get params
*
*/
ParametersAction parametersAction = build.getAction(ParametersAction)
parametersAction.parameters.each { ParameterValue v ->
    paramsKV."${v.getName()}" = v.getValue()
    if (!v.getValue()){
        println ("Parameter ${v.getName()} doesn't have value!") 
    }
}
build.setDisplayName(paramsKV.ConfigFile)
// For using Global params in config page, just use the name directly.
def gitScmSource = [branch:JenkinsfileSandboxBranch, 
                    url:JenkinsfileURL, 
                    credential:CredentailHelper.GetcredentialByDescription(this ,GitCredential)]



PipelineHelper.GenProjJob(this, jm.readFileInWorkspace("Pipelines/${paramsKV.ConfigFile}"), gitScmSource)