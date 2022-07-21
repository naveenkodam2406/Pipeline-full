package lib


class SVNPipeline implements Serializable{
    static UserRemoteConfigs(agrsMap){
        if (agrsMap.Credential){
            return Utility.GetcredentialByDescription(agrsMap.Credential)
        }
    }
    static CheckOut(script, agrsMap){
        // https://github.com/jenkinsci/subversion-plugin/blob/master/src/main/java/hudson/scm/SubversionChangeLogSet.java
        
        def SvnLocalCFG = [cancelProcessOnExternalsFail: true, credentialsId: SVNPipeline.UserRemoteConfigs(agrsMap), 
                            depthOption: 'infinity', ignoreExternalsOption: true, remote: agrsMap.RemoteURL]
        
        if (agrsMap.RelativeTargetDir != null){
            SvnLocalCFG.add([local:agrsMap.RelativeTargetDir])
        }
        def ScmMapping = [$class: Utility.SCMClass.SvnSCM.getClassName(), additionalCredentials: [], 
                        // excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', excludedUsers: '', 
                        filterChangelog: false, ignoreDirPropChanges: false, includedRegions: '', 
                        locations: [SvnLocalCFG], 
                        quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']]
        if(agrsMap.ExcludedUsers != null){
            ScmMapping.add([excludedUsers:agrsMap.ExcludedUsers])
        }
        if(agrsMap.ExcludedCommitMessages != null){
            ScmMapping.add([excludedCommitMessages:agrsMap.ExcludedCommitMessages])
        }
        if(agrsMap.ExcludedRegions != null){
            ScmMapping.add([excludedRegions:agrsMap.ExcludedRegions])
        }
        if(agrsMap.ExcludedRevprop != null){
            ScmMapping.add([excludedRevprop:agrsMap.ExcludedRevprop])
        }
        def scm = script.checkout(ScmMapping)
        return scm
    }
}