import lib.Utility
 // kwargs
// kwargs.xcodeProjPath
// kwargs.xcodeArchivePath
// kwargs.plist
// kwargs.plistPath "gameClientSettings\\projectU\\${env.Region}
// kwargs.outputRoot
// kwargs.discipline
// kwargs.bundleId
// kwargs.teamId  -> DEVELOPMENT_TEAM=${kwargs.teamId}
// kwargs.codeName -> projectu
// kwargs.pbxproj
// kwargs.gameConfig
// kwargs.lebianVersionUpdate boolean
// kwargs.pdsdk_config nullable & boolean
// kwargs.archiveOpt = " -destination generic/platform=iOS" 

// env
// env.Region
// env.IOSName
// env.ProductName
// env.versionCode
def call(kwargs=[:]){
    def newProjPath = kwargs.xcodeProjPath + "_working"
    def newArchivePath = kwargs.xcodeArchivePath
    dir(newArchivePath){
        deleteDir()
    }
    def ipaName = env.IOSName.replace("{Discipline}", kwargs.discipline)
    if(kwargs.archiveOpt == null) kwargs.archiveOpt = ""
    sh("""cp -R ${kwargs.xcodeProjPath} ${newProjPath}""")
    def infoPlist = "${newProjPath}/info.plist"
    if(kwargs.teamId){
        kwargs.archiveOpt += " DEVELOPMENT_TEAM=${kwargs.teamId} "
    }
    if(kwargs.lebianVersionUpdate){
        def verStr = env.versionCode.tokenize(".")
        def lebianVersion = (verStr[0].toInteger()*10000 + verStr[1].toInteger()*100 + verStr[2].toInteger())
        echo("versionCode: ${versionCode} -> lebianVersion:${lebianVersion}")
        sh("/usr/libexec/PlistBuddy -c 'Set :LEBIAN_VERCODE ${lebianVersion}' ${infoPlist} ")
    }
    sh("/usr/libexec/PlistBuddy -c 'Set :CFBundleIdentifier ${kwargs.bundleId}' ${infoPlist} ")
    if(kwargs.pdsdk_config != null){
        if(env.Region.toLowerCase() == "tw"){
            sh("/usr/libexec/PlistBuddy -c 'Set :pdsdk_config:isDebug ${kwargs.pdsdk_config}'  ${infoPlist} ")
        }
        else if(env.Region.toLowerCase() == "cn"){
            sh("/usr/libexec/PlistBuddy -c 'Set :PDSDK_FLAG_DEBUG ${kwargs.pdsdk_config}'  ${infoPlist} ")
        }
    }
    if(kwargs.pbxproj){
        sh("rm -f ${newProjPath}/Unity-iPhone.xcodeproj/project.pbxproj")
        sh("cp ${newProjPath}/${kwargs.pbxproj} ${newProjPath}/Unity-iPhone.xcodeproj/project.pbxproj")
    }
    if(kwargs.gameConfig){
        sh("rm -f ${newProjPath}/Data/Raw/Config/GameConfig.txt")
        sh("cp ${env.GitRootIOS}/${env.ProjectPathClient}/GameConfig/${kwargs.gameConfig} ${newProjPath}/Data/Raw/Config/GameConfig.txt")
    }
    
    xcodebuild("-project ${newProjPath}/Unity-iPhone.xcodeproj -scheme Unity-iPhone -archivePath ${newArchivePath}/${kwargs.codeName}.xcarchive clean archive build ${kwargs.archiveOpt}")
    writeFile(file:kwargs.plist, text:libraryResource("${kwargs.plistPath}\\${kwargs.plist}"))
    xcodebuild(" -exportArchive -archivePath ${newArchivePath}/${kwargs.codeName}.xcarchive -exportPath ${kwargs.outputRoot}/${kwargs.discipline} -exportOptionsPlist ${kwargs.plist}")
    def productName = env.ProductName
    def newName = Utility.RegExFind(readFile("${newProjPath}/Unity-iPhone.xcodeproj/project.pbxproj"),"PRODUCT_NAME =\\s{0,}\\w+")
    if(newName != null){
        productName = newName.split("=")[1].trim()
    }
    sh("mv ${kwargs.outputRoot}/${kwargs.discipline}/${productName}.ipa ${kwargs.outputRoot}/IOS/${ipaName}")
}