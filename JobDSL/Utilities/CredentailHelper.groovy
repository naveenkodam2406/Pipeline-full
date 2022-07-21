package Utilities


class CredentailHelper implements Serializable {
    static credentialXML = "../../credentials.xml"  //relavent path to the job workspace
    static credKVs = [:]
    static getcredKVs = { jm->
        if (credKVs.size() == 0) {
            new XmlSlurper().parseText(jm.readFileInWorkspace(credentialXML)).'**'
            .findAll{ node-> node.id.name() == 'id'}
            .collect({ credKVs.put(it.id.text(), it.description.text())})
        }
        return credKVs
    } 
    static GetcredentialByDescription(jmContext , description) {
        def id = ""
        if(!description) return id
        getcredKVs(jmContext.jm).each {k, v -> if (v.contains(description)) {
            id = k
            }
        }
        return id
    }
}