package lib
import java.lang.*
import jenkins.model.*

class P4Pipeline implements Serializable  {
    static Checkout(script,agrsMap){
        if (script.env.Changelist != null && script.env.Changelist != ""){
            def cl = script.env.Changelist
            script.echo "Sync to ${cl}"
            agrsMap.Label = cl
        }
        if (script.env.ForceSync == "true"){
            agrsMap.ForceSync = true 
        }
        def scmMap = P4Pipeline.WorkspaceMapConf(script,agrsMap)
        def scmEnv = script.checkout(scmMap)
        return scmEnv
    }
    static WorkspaceMapConf(script,agrsMap){
        def nodeName = script.env.NODE_NAME
        agrsMap.WorkspaceNameFormat = agrsMap.WorkspaceNameFormat.replace("{NODE_NAME}", "${nodeName}")
        if (agrsMap.ClientSpec){
            agrsMap.ClientSpec = agrsMap.ClientSpec.replace("{PLACEHOLDER}", "${agrsMap.WorkspaceNameFormat}")
        }
        def scmEnv = null
        def scmMap = [  changelog:  agrsMap.Changelog, poll:  agrsMap.Poll, scm:  this.scmConfig(agrsMap) ]
        return scmMap
        
    }
    static Map scmConfig (agrsMap, ignoreNonCredError = false) {
        def credId = Utility.GetcredentialByDescription(agrsMap.Credential)
        if (!credId && !ignoreNonCredError){
            throw new Exception("Credential not Found by the given Description: ${agrsMap.Credential}")
            return null
        }
        def scmMap = [:]
        scmMap = [  $class      : Utility.SCMClass.PerforceScm.getClassName(),
                    credential  : credId ]

        def workspaceMap = [    $class  : Utility.SCMClass."${agrsMap.WorkspaceClass}".getClassName(), 
                                charset : "none", pinHost: false]

        if (Utility.SCMClass."${agrsMap.WorkspaceClass}" != Utility.SCMClass.StreamWorkspaceImpl){
            workspaceMap.name = agrsMap.WorkspaceNameFormat
        }
        else {
            workspaceMap.format = agrsMap.WorkspaceNameFormat
        }
        def populateSetting = Utility.Populate.SyncOnlyImpl
        if (agrsMap.Populate){
            // take the short ClassName as the enum object
        	populateSetting = Utility.Populate."${agrsMap.Populate}"
        }
        def specMap = [:]
        if ( Utility.SCMClass."${agrsMap.WorkspaceClass}" != Utility.SCMClass.StreamWorkspaceImpl){
            specMap = [
                allwrite    : false, 
                backup      : false, 
                clobber     : true, 
                compress    : false, 
                line        : "LOCAL", 
                locked      : false, 
                modtime     : false, 
                rmdir       : true, // to remove folder while there is no file in it.
                serverID    : "", 
                streamName  : agrsMap.ClientSpecStreamName ? agrsMap.ClientSpecStreamName : "",  
                type        : "WRITABLE", 
                view        : agrsMap.ClientSpec
            ] 
            workspaceMap.spec = specMap
        }
        if (Utility.SCMClass."${agrsMap.WorkspaceClass}" == Utility.SCMClass.StreamWorkspaceImpl){
            workspaceMap.streamName = agrsMap.ClientSpec
        }
        scmMap.populate = populateSetting.getPopulateImpl()
        if (agrsMap.Filters){
            def fl = agrsMap.Filters
            def filters = []
            for (int i = 0; i < fl.size(); i++){
                filters.add([$class: Utility.Filter."${fl[i].Class}".getClassName(), (Utility.Filter."${fl[i].Class}".getKeyName()):fl[i].FilterValue ])
            }
            scmMap.filter = filters
        }
        if (agrsMap.Label){
            scmMap.populate.pin = agrsMap.Label
        }
        if (agrsMap.ForceSync){
            scmMap.populate.force = agrsMap.ForceSync
        }
        scmMap.workspace = workspaceMap
        return scmMap
    }
}