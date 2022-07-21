import lib.Utility
// env params
// env.UploadABFolder
// env.BuildNumberForDLC

// BuildArgs.GameClientSetting
// "GameClientSetting":[
//     "${ProjectPathClient}/Assets/GameProject/Resources/GameClientSetting.asset":["AssetBundleDownloadUrlRoot.*":"AssetBundleDownloadUrlRoot: ${params.AssetBundleDownloadUrlRoot}/{params.versionPrefix}"]
// ],
def call(BuildArgs,GitRoot){
    if(BuildArgs.GameClientSetting){
        BuildArgs.GameClientSetting.each{k,v ->l:{
            if(env.BuildNumberForDLC=="true"){
                env.UploadABFolder = env.BUILD_NUMBER
            }
            def filePath = Utility.OS(this).Path(GitRoot + "\\" +k)
            def clientconf = readFile(filePath)
            v.each{reg, val->j:{
                clientconf = clientconf.replace(Utility.RegExFind(clientconf,reg), val)
                writeFile(file: filePath, text: clientconf.replace("{params.versionPrefix}","${env.UploadABFolder}"), encoding: "UTF-8")
                print (readFile(filePath))
            }}
        }}
    }
}