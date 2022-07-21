/*
 env params
*/

// env.ResaveFolder
// env.GitRootIOS
// env.MountCred
// env.OutputShareXCodeProj
// env.PackageName
// env.IOSName
// env.ProductName
// env.FailFast

/*
 kwargs params 
*/

// kwargs.mounted
// kwargs.outputRoot
// kwargs.xcodeProjPath
// kwargs.xcodeArchivePath
// kwargs.plistResourceFolder

import lib.*
def call (kwargs=[:]){
parallel(
"Compress":{
    stage("Compress"){
        if(!env.OutputShareXCodeProj) error ("OutputShareXCodeProj is not config'ed in Environment")
        def splitP = env.OutputShareXCodeProj.tokenize("\\")
        def serverPath = splitP[0]+"/"+ splitP[1]
        def mountedXCodeFolder = null
        compress(zip:"${kwargs.outputRoot}/IOS/Resave.zip" , path:env.GitRootIOS +"/"+env.ResaveFolder, python:"/usr/local/bin/python")
        try{
            
            if (serverPath == kwargs.mounted[2]){
                mountedXCodeFolder = kwargs.mounted
            }
            else{
                mountedXCodeFolder = mount(path:env.OutputShareXCodeProj, cred:env.MountCred)
            }
            compress(zip:"${mountedXCodeFolder[0]}/xcode_${env.PackageName}.zip",path:"${kwargs.xcodeProjPath}", mode:"1" , python:"/usr/local/bin/python")
        }
        finally{
            if(mountedXCodeFolder[1] != kwargs.mounted[1]){
                unmount(mountedXCodeFolder[1])
            }
        }
    }
},


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

"ZiHao_QA":{
    stage("ZiHao_QA"){
        iosExport(xcodeProjPath: kwargs.xcodeProjPath, xcodeArchivePath: kwargs.xcodeArchivePath, plist:"ExportOptions.plist",
            plistPath: kwargs.plistResourceFolder, outputRoot: kwargs.outputRoot, discipline: "ZiHao_QA",
            bundleId: "com.blackjackstudio.projectu", teamId: "9HAZKCF2WP",
            codeName: "projectu"
        )
    }
},
"PDIosQa":{
    stage("PDIosQa"){
        def bid = "com.gamebeans.tdj"
        if(env.Region.toLowerCase() == "cn"){
            bid = "com.zlongame.tdj"
        } 
        iosExport(xcodeProjPath: kwargs.xcodeProjPath, xcodeArchivePath: kwargs.xcodeArchivePath, plist:"QA_ExportOptions.plist",
            plistPath: kwargs.plistResourceFolder, outputRoot: kwargs.outputRoot, discipline: "PDIosQa",lebianVersionUpdate: true,
            bundleId: bid, pdsdk_config:true,  gameConfig: "IOS_PDQA_GameConfig.txt", pbxproj:"project_PDIosQa.pbxproj",archiveOpt:"-destination generic/platform=iOS",
            codeName: "projectu"
        )
    }
},
"PDAppStore":{
    stage("PDAppStore"){
        def bid = "com.gamebeans.tdj"
        if(env.Region.toLowerCase() == "cn"){
            bid = "com.zlongame.tdj"
        } 
        def archivePath = iosExport(xcodeProjPath: kwargs.xcodeProjPath, xcodeArchivePath: kwargs.xcodeArchivePath, plist:"AppStory_ExportOptions.plist",
            plistPath: kwargs.plistResourceFolder, outputRoot: kwargs.outputRoot, discipline: "PDAppStore",lebianVersionUpdate: true,
            bundleId: bid, pdsdk_config:false, gameConfig: "IOS_AppStore_GameConfig.txt",pbxproj:"project_PDAppStore.pbxproj",archiveOpt:"-destination generic/platform=iOS",
            codeName: "projectu"
        )
        compress(zip: kwargs.outputRoot + "/IOS/dSYM.zip" , 
        path: archivePath +"/projectu.xcarchive/dSYMs/projectu.app.dSYM" , mode:"1" , relpath:"." , python:"/usr/local/bin/python")
    }
},
failFast:env.FailFast == "true"?true:false
)
}