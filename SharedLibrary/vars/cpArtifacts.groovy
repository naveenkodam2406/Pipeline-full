import lib.Utility
import hudson.model.*


def call (conf=[:]){
    def m_buildNo = conf.buildNo
    def m_build = Utility.GetBuildInfo(conf)
    if(m_build == null) error("no build found") 
    def m_result = [:]
    def m_artifacts = []
    
    if(m_build.artifacts){
        for(artifact in m_build.artifacts){
            for(f in conf.filter){
                if(Utility.RegExFind(artifact.fileName, f)){
                    m_artifacts.add(artifact.fileName)
                    copyArtifacts(filter: artifact.relativePath , flatten: true, 
                    projectName: conf.projectName, 
                    selector: specific("${m_build.buildNumber}"))
                   // convert to string according to the following error
                        // java.lang.ClassCastException: hudson.plugins.copyartifact.SpecificBuildSelector.buildNumber 
                        // expects class java.lang.String but received class java.lang.Integer
                }
            }
        }
    }
    if(m_artifacts.size() == 0 ) error("No Artifacts Found")
    m_result["CopyArtifact_Artifacts"] = m_artifacts
    m_result["CopyArtifact_BuildURL"] = m_build.url
    m_result["CopyArtifact_BuildNo"] = m_build.buildNumber
    return m_result
}