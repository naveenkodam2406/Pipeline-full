
import lib.Utility
def call(ArrayList stringLookingFor ,int last_N_Line = 2500, String rawContent = null){
    def rs = []
    if(rawContent == null){
        rawContent = currentBuild.rawBuild.getLog(last_N_Line).join('\n')
    }
    stringLookingFor.each{
        def findstr = Utility.RegExFind(rawContent, ".*"+it+".*")
        if (findstr != null){
            rs.add(findstr)
        }
    }
    return rs;
}