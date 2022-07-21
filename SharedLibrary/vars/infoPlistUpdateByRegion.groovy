// Region String in env map
def call(infoPlist){
    def verStr = env.versionCode.tokenize(".")
    def lebianVersion = (verStr[0].toInteger()*10000 + verStr[1].toInteger()*100 + verStr[2].toInteger())
    echo("versionCode: ${versionCode} -> lebianVersion:${lebianVersion}")
    sh("/usr/libexec/PlistBuddy -c 'Set :LEBIAN_VERCODE ${lebianVersion}' ${infoPlist} ")
    if(env.Region.toLowerCase() == "tw"){
        sh("/usr/libexec/PlistBuddy -c 'Set :CFBundleIdentifier com.gamebeans.tdj' ${infoPlist} ")
        sh("/usr/libexec/PlistBuddy -c 'Set :pdsdk_config:isDebug true'  ${infoPlist} ")
    }
    else if(env.Region.toLowerCase() == "cn"){
        sh("/usr/libexec/PlistBuddy -c 'Set :CFBundleIdentifier com.zlongame.tdj' ${infoPlist} ")
        sh("/usr/libexec/PlistBuddy -c 'Set :PDSDK_FLAG_DEBUG true'  ${infoPlist} ")
    }
}
