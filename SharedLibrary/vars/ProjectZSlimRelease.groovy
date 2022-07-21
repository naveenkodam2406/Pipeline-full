import lib.*
import lib.email.*
import lib.dingding.*

def call(BuildArgs) {
    def scmRS = []
    def scmList = []
    def clientBuildMD5 = ""
    def manifestVersion = [:]
    def nodeList = []
    def clientAbMD5 =""
    pipeline{
        agent none
        options {
            timestamps()
            timeout(time: 5, unit: 'HOURS')
            skipDefaultCheckout true
            buildDiscarder(logRotator(numToKeepStr: "20"))
        }
        stages{
            stage("EnvSetup"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            Utility.UpdateJobStatusOnELK(this)
                        }
                        def envParams =  BuildArgs.EnvironmentParams?(BuildArgs.EnvironmentParams << params):params
                        Utility.SetEnvParams(this, envParams)
                        env.Options = env.Options.replace("#false","#0").replace("#true","#1")
                        env.SCMStr = ""
                        manifestVersion["ClientVersion"] = env.versionCode
                        currentBuild.description = "${env.BUILD_NUMBER}_${env.version}_${env.versionPrefix}"
                        env.Options.split(" ").each{
                            if(it.contains("versionCode")){
                                def tmp = it.split("#")[1].trim()
                                currentBuild.description += "_${tmp}"
                            }
                            else if(it.contains("bundleVersionStep")){
                                currentBuild.description += "_${it}"
                            }
                        }
                        def suffix = ""
                        if(params.JustBuildAB == true){
                            suffix +="_AB"
                            if(params.BuildServer == true){
                                suffix +="_Server"
                            }
                        }
                        else if(params.BuildClient == false && params.BuildServer == true ){
                            suffix +="_Server"
                        }
                        env.OutputShare = env.OutputShare.replace("_{PLACEHOLDER}",suffix)
                        //env.OutputABHotfixTarget = env.OutputABHotfixTarget.replace("_{PLACEHOLDER}",suffix)
                        currentBuild.description += "<br/>" + env.OutputShare
                    }
                }
            }
            stage("Git Pull"){
                when{expression { params.GitPull != false }}
                steps{
                    script{
                        parallel(
                            // "Git Pull Windows":{
                            //     stage('Git Pull Windows') {
                            //         if(params.Windows == true){
                            //             node(BuildArgs.AssignedLabel_Win){
                            //                 ws(env.GitRootWin){}
                            //                 dir(env.GitRootWin){
                            //                     def buildargs = [:] << BuildArgs
                            //                     scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                            //                     bat("""${env.LinkScript} """)
                            //                 }
                            //             }
                            //         }
                            //         else
                            //         {
                            //             Utility.MarkStageAsSkipped(STAGE_NAME)
                            //         }
                            //     }
                            // },
                            'Git Pull Android&Server':{
                                stage('Git Pull Android&Server') {
                                    if(params.Android == true){
                                        node(BuildArgs.AssignedLabel_Android){
                                            ws(env.GitRootAndroid){}
                                            dir(env.GitRootAndroid){
                                                def buildargs = [:] << BuildArgs
                                                scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                                                bat("""${env.LinkScript} """)
                                            }
                                            gitLogM1([env.GitRootAndroid],"Android",BuildArgs.Submodule)
                                        }
                                    }
                                    else
                                    {
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            },
                            // 'Git Pull IOS':{
                            //     stage('Git Pull IOS') {
                            //         if( params.IOS == true ){
                            //             node(BuildArgs.AssignedLabel_IOS){
                            //                 ws(env.GitRootIOS){
                            //                     def buildargs = [:] << BuildArgs
                            //                     scmRS.add(GitPipeline.CheckOutWithConfSetup(this, buildargs, BuildArgs.Submodule))
                            //                     dir(env.LinkScriptWS){
                            //                         sh("chmod 777 ./${env.LinkScriptIOS}")
                            //                         sh("./${env.LinkScriptIOS}")
                            //                     }
                            //                 }
                            //             }
                            //         }
                            //         else{
                            //             Utility.MarkStageAsSkipped(STAGE_NAME)
                            //         }
                            //     }
                            // }
                        )
                        if(scmRS.size() > 0){
                            print scmRS
                            Utility.SetDisplayName(scmRS, currentBuild, scmList,true)
                            def elkscmdata =[:]
                            elkscmdata["SCM"]= scmList
                            env.SCMStr = Utility.ObjToJsonStr(elkscmdata).replace("\"","'")
                            env.SCMStr = env.SCMStr.substring(1,env.SCMStr.length()-1 )+","
                            changeListsFileV2(scmRS,BuildArgs.AssignedLabel)
                        }
                    }
                }
            }
            stage("Excel ConfAnalys"){
                when{expression { params.ExcelConfAnalys != false }}
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            excelConfAnalysis(env.GitRootAndroid)
                            stage("Server Conf"){
                                msbuild(""" ${env.GitRootServer}\\Server\\ProjectZ.ConfigDataLoader\\ProjectZ.ConfigDataLoader.csproj /p:Configuration="Release" /p:Platform="AnyCPU" """)
                                msbuild(""" ${env.GitRootServer}\\Server\\ProjectZ.ConfigDataLoader\\ProjectZ.ConfigDataLoader.csproj /p:Configuration="Debug" /p:Platform="AnyCPU" """)
                            }
                            stage("ClientSync ExcelConf"){
                                unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootAndroid +"\\"+env.ProjectPathClient, 
                                                    executeMethod:env.ExecClientSyncExcelConf,logKeyWords:["GenerateConfigDataAsset complete"],
                                                    last_N_Line:2500, logToFile:false,)
                            }
                            stage("ClientSync CommonCode"){
                                unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootAndroid +"\\"+env.ProjectPathClient, 
                                                    executeMethod:env.ExecClientSyncCommonCode,logKeyWords:["ImportOutSideCommonCode complete"],
                                                    last_N_Line:2500, logToFile:false,)
                            }
                            stage("GitPush AutoGen"){
                                if(params.GitPushAutoGen != false){
                                    if(BuildArgs.GitPushPreBuild){
                                        BuildArgs.GitPushPreBuild.each{k,v ->l:{
                                            if(v.size() == 1 && v[0] =="*.*"){
                                                dir(k){
                                                    //all ready use -A in the cmd
                                                    gitAddFile(".", params.Dryrun)
                                                }
                                            }
                                            else{
                                                def files = []
                                                v.each{ f->j:{ getFilePathByName(k,f).each{
                                                    files.add(it)
                                                }}}
                                                dir(k){
                                                    gitAddFile(files, params.Dryrun)
                                                }
                                            }
                                            
                                        }}
                                        if(scmList.size()>0){
                                            scmList.each{
                                                dir(Utility.OS(this).Path(env.GitRootAndroid + "\\" + it["GIT_CHECKOUT_DIR"])){
                                                    gitCommit("Conf Updated By Jenkins ${env.BUILD_URL}", params.Dryrun)
                                                }
                                            }
                                        }else{
                                            dir(env.GitRootAndroid){
                                                gitCommit("Conf Updated By Jenkins ${env.BUILD_URL}", params.Dryrun)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            stage("Build"){
                steps{
                    script{
                        parallel(
                            // "IOS":{
                            //     stage("IOS"){
                            //         if(params.IOS == true  && params.BuildClient == true ){
                            //             node(BuildArgs.AssignedLabel_IOS){
                            //                 def mountedFolder = mount(path:env.OutputShare, cred:env.MountCred)
                            //                 try{
                            //                     if(env."BundleDataiOS.asset_FILENAME" == "BundleDataiOS.asset"){
                            //                         unstash "BundleDataiOS.asset"
                            //                         def bundleAsset = readFile("BundleDataiOS.asset")
                            //                         if(bundleAsset!=""){
                            //                             writeFile(file:env.GitRootIOS +"/"+env.ProjectPathClient+"/Assets/GameProject/RuntimeAssets/BundleDataiOS.asset", text:bundleAsset)
                            //                         }
                            //                     }
                            //                     dir(env.GitRootIOS +"/"+env.ProjectPathClient+"/ResourceCheckResult"){
                            //                         deleteDir()
                            //                     }
                            //                     if(BuildArgs.GameClientSetting){
                            //                         BuildArgs.GameClientSetting.each{k,v ->l:{
                            //                             print v
                            //                             writeFile(file: v, text: libraryResource(k).replace("{params.versionPrefix}","${params.versionPrefix}"), encoding: "UTF-8")
                            //                         }}
                            //                     }
                            //                     def outputRoot = "${env.GitRootIOS}/${env.OutputFolderRoot}/${env.BUILD_NUMBER}"
                            //                     def xcodeProjPath = "${env.GitRootIOS}/${env.OutputFolderRoot}/${env.IOSProjectName}"
                            //                     def xcodeArchivePath = "${env.GitRootIOS}/${env.OutputFolderRoot}/${env.IOSProjectName}_archive"
                            //                     stage('Build IOS') {
                            //                         if(params.BuildClient == true || params.JustBuildAB == true){
                            //                             // clean previous build output
                            //                             dir("${outputRoot}"){deleteDir()}
                            //                              unityExec(unityExePath:env.UnityPathIOS, projectPath:env.GitRootIOS +"/"+env.ProjectPathClient, 
                            //                                 executeMethod:env.ExecBuildIOS, options:env.Options, output:outputRoot, apk:env.IOSProjectName,
                            //                                 logKeyWords:BuildArgs.UnityErrorCheck,
                            //                                 last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,
                            //                                 buildAssetBundle:env.ExecBuildABIOS)
                            //                             withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(env.MacBuildAccount), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                            //                                 sh("""security unlock-keychain -p ${pwd}""")
                            //                             }
                            //                             sh("mkdir -p ${outputRoot}/IOS")
                            //                         }
                            //                         else{
                            //                             Utility.MarkStageAsSkipped(STAGE_NAME)
                            //                         }
                            //                     }
                            //                     stage("Export iOS"){
                            //                         if(params.JustBuildAB != true){
                            //                             parallel(
                            //                                 "Compress":{
                            //                                     stage("Compress"){
                            //                                         compress(zip:"${outputRoot}/IOS/Resave.zip" , path:env.GitRootIOS +"/"+env.ResaveFolder, python:"/usr/local/bin/python")
                            //                                         if(params.UploadXcodeProject){
                            //                                             compress(zip:"${mountedFolder[0]}/xcode_${env.PackageName}.zip",path:"${xcodeProjPath}", mode:"1" , python:"/usr/local/bin/python")
                            //                                         }
                            //                                     }
                            //                                 },
                            //                                 "ZiHao_QA":{
                            //                                     stage("ZiHao_QA"){
                            //                                         def newProjPath = xcodeProjPath + "_ZiHao"
                            //                                         def newArchivePath = xcodeArchivePath + "_ZiHao"
                            //                                         def ipaName = env.IOSName.replace("{Discipline}", "ZiHao_QA")
                            //                                         sh("""cp -R ${xcodeProjPath} ${newProjPath}""")
                            //                                         def infoPlist = "${newProjPath}/info.plist"
                            //                                         xcodebuild("-project ${newProjPath}/Unity-iPhone.xcodeproj -scheme Unity-iPhone -archivePath ${newArchivePath}/projectu.xcarchive clean archive build DEVELOPMENT_TEAM=9HAZKCF2WP")
                            //                                         writeFile(file:"ExportOptions.plist", text:libraryResource("gameClientSettings\\projectFM\\${env.Region}\\ExportOptions.plist"))
                            //                                         sh("/usr/libexec/PlistBuddy -c 'Set :CFBundleIdentifier com.blackjackstudio.projectu' ${infoPlist} ")
                            //                                         xcodebuild(" -exportArchive -archivePath ${newArchivePath}/projectu.xcarchive -exportPath ${outputRoot}/ZiHao_QA -exportOptionsPlist ExportOptions.plist")
                            //                                         def productName = env.ProductName
                            //                                         def newName = Utility.RegExFind(readFile("${newProjPath}/Unity-iPhone.xcodeproj/project.pbxproj"),"PRODUCT_NAME =\\s{0,}\\w+")
                            //                                         if(newName != null){
                            //                                             productName = newName.split("=")[1].trim()
                            //                                         }
                            //                                         sh("mv ${outputRoot}/ZiHao_QA/${productName}.ipa ${outputRoot}/IOS/${ipaName}")
                            //                                     }
                            //                                 }
                            //                             )
                                                        
                            //                         }
                            //                         else{
                            //                             Utility.MarkStageAsSkipped(STAGE_NAME)
                            //                         }
                            //                     }
                            //                     stage("Release IOS"){
                            //                         sh("mkdir -p ${mountedFolder[0]}/${env.OutputShareAB}")
                            //                         sh("""rsync -r --include "*/" --include "*.manifest" --exclude "*"  ${env.GitRootIOS}/${env.ProjectPathClient}/AssetBundles/ ${mountedFolder[0]}/${env.OutputShareAB}/manifest_${env.BUILD_NUMBER}""")
                            //                         sh("""rsync -r --include "*/" --exclude "*.manifest"  ${env.GitRootIOS}/${env.ProjectPathClient}/AssetBundles/ ${mountedFolder[0]}/${env.OutputShareAB}""")
                            //                         manifestVersion["iOSBundleVersion"] = readFile(env.GitRootIOS +"/"+env.ProjectPathClient+"/AssetBundles/iOS/BundleDataVersion.txt").tokenize(",")[0]
                            //                         sh("mkdir -p ${mountedFolder[0]}/${env.OutputShareClient}")
                            //                         sh("""rsync -r ${outputRoot}/IOS ${mountedFolder[0]}/${env.OutputShareClient}""")
                            //                         //mdFive(path:outputRoot+"/IOS", relpath:"..", python:"/usr/local/bin/python")
                            //                         sh("find ${mountedFolder[0]} -name '.DS_Store' -type f -delete")
                            //                         //clientBuildMD5 += readFile("${outputRoot}/IOS/VerifyMd5.txt")
                            //                         def ipaName = env.IOSName.replace("{Discipline}", "ZiHao_QA")
                            //                         if(BuildArgs.ClientUploadFTP){
                            //                             BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                            //                                 withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                            //                                     ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/ios/${env.PackageName}", path:"${outputRoot}/IOS/${ipaName}")
                            //                                     ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/ios/${env.PackageName}", path:env.GitRootIOS +"/"+env.ResaveFolder)
                            //                                 }
                            //                             }}
                            //                         }
                            //                     }
                            //                 }
                            //                 finally{
                            //                     dir(env.GitRootIOS +"/"+env.ProjectPathClient+"/ResourceCheckResult"){
                            //                         archiveArtifacts artifacts: "BundleAssetCheckResult*.txt,*.xml,*.svg", followSymlinks: false, allowEmptyArchive : true
                            //                     }
                            //                     sh("umount ${mountedFolder[1]}")
                            //                     sh("rmdir ${mountedFolder[1]}")

                            //                     Utility.CleanParallelJobWorkSpace(this)
                            //                 }
                            //             }
                            //         }
                            //         else
                            //         {
                            //             Utility.MarkStageAsSkipped(STAGE_NAME)
                            //         }
                            //     }
                            // },
                            "Server":{
                                stage("Server"){
                                    if(params.BuildServer == true){
                                        node(BuildArgs.AssignedLabel_Server){
                                            print(NODE_NAME)
                                            try{
                                                def outputRoot = "${env.GitRootServer}\\${env.OutputFolderRoot}\\server"
                                                cleanOutput(outputRoot)
                                                def serverBuildType = "Debug"
                                                stage('Build Server') {
                                                    // clean previous build output
                                                    if(params.version == "release"){
                                                        serverBuildType = "Release"
                                                    }
                                                    cleanOutput(env.GitRootServer+"\\ServerBin")
                                                    dir("${env.GitRootServer}"){
                                                        timeout(5){ // adding 5 min timeout to fail the build if the cmd gets stuck
                                                            msbuild(solution:env.ServerSln, opt: env."${serverBuildType}.ServerBuildOpt")
                                                        }
                                                    }
                                                }
                                                stage('Release Server') {
                                                    dir("${env.GitRootServer}"){
                                                        bat(env."${serverBuildType}.ServerRelease")
                                                    }
                                                    def ver = Utility.ObjToJsonStr([ServerVersion:env.BuildId],true)
                                                    writeFile(file: env.GitRootServer+"\\ServerBin\\version.txt", text:ver)
                                                    mdFive(path:env.GitRootServer+"\\ServerBin")
                                                    robocopy(env.GitRootServer+"\\ServerBin","${outputRoot}\\Server","/MIR")
                                                    robocopy("${outputRoot}\\Server",env.OutputShare+"\\"+env.OutputShareServer,"/MIR")
                                                    packServer(env.GitRootServer+"\\ServerBin", null, BuildArgs.ServerConf)
                                                    print "Release Server"
                                                }
                                                stage('Upload Server') {
                                                    try{
                                                        def zipName = "${outputRoot}\\Server.zip"
                                                        compress(zip:zipName, path:"${outputRoot}\\Server", mode:"1",relpath:"..")
                                                        print ("Upload Server ${outputRoot}\\Server.zip")
                                                        if(BuildArgs.ServerUploadFTP){
                                                            uploadServerV2(path:outputRoot, file: "Server.zip", serverConfPath:BuildArgs.ServerUploadFTP, 
                                                            serverTag:env.UpdateServerTag, port:12099, opt:"-pasv 1" )
                                                        }
                                                    }
                                                    catch(Exception ex){
                                                        // set unstable not fail the build
                                                        unstable(message: "Upload to FTP is unstable")
                                                        print ex
                                                    }
                                                }
                                            }
                                            finally{
                                                Utility.CleanParallelJobWorkSpace(this)
                                            }
                                        }
                                    }
                                    else{
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            }, 
                            // "Windows":{
                            //     stage("Windows"){
                            //         if(params.Windows == true  && params.BuildClient == true ) {
                            //             node(BuildArgs.AssignedLabel_Win){
                            //                 try{
                            //                     def outputRoot = "${env.GitRootWin}\\${env.OutputFolderRoot}"
                            //                     dir(env.GitRootWin +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                            //                         deleteDir()
                            //                     }
                            //                     stage('Build Windows') {
                            //                         if(params.BuildClient == true || params.JustBuildAB == true){
                            //                             if(env."BundleDataStandaloneWindows64.asset_FILENAME" == "BundleDataStandaloneWindows64.asset"){
                            //                                 unstash "BundleDataStandaloneWindows64.asset"
                            //                                 def bundleAsset = readFile("BundleDataStandaloneWindows64.asset")
                            //                                 if(bundleAsset!=""){
                            //                                     writeFile(file:env.GitRootWin +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets\\BundleDataStandaloneWindows64.asset", text:bundleAsset)
                            //                                 }
                            //                             }
                            //                             // clean previous build output
                            //                             dir(outputRoot){deleteDir()}
                            //                             gameClientSettings(BuildArgs, env.GitRootWin)
                            //                             unityExec(unityExePath:env.UnityPathWin, projectPath:env.UnityPathWin +"\\"+env.ProjectPathClient, 
                            //                                 executeMethod:env.ExecBuildWin, options:env.Options, output:"${outputRoot}\\${env.ProductName}", apk:env.WinName,
                            //                                 logKeyWords:BuildArgs.UnityErrorCheck,
                            //                                 last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:env.WinName,
                            //                                 buildAssetBundle:env.ExecBuildABWin)
                            //                         }
                            //                         else{
                            //                             Utility.MarkStageAsSkipped(STAGE_NAME)
                            //                         }
                            //                     }
                            //                     stage('Release Windows') {
                            //                         if(params.JustBuildAB != true){
                            //                             compress(zip:"${outputRoot}\\Windows\\${env.PackageName}.zip", 
                            //                             path:"${outputRoot}\\${env.ProductName}", mode:"1",relpath:"..")
                            //                             // mdFive(path:outputRoot+"\\Windows", relpath:"..")
                            //                             // clientBuildMD5 += readFile("${outputRoot}\\Windows\\VerifyMd5.txt")
                            //                             robocopy("${outputRoot}\\Windows", env.OutputShare+"\\"+env.OutputShareClient+"\\Windows" ," ${env.PackageName}.zip ")
                            //                             if(BuildArgs.ClientUploadFTP){
                            //                                 BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                            //                                     withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                            //                                         ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/windows", path:"${outputRoot}\\Windows\\${env.PackageName}.zip")
                            //                                     }
                            //                                 }}
                            //                             }
                                                        
                            //                         }
                            //                         else{
                            //                             Utility.MarkStageAsSkipped(STAGE_NAME)
                            //                         }
                            //                     }
                            //                 }
                            //                 finally{
                            //                     dir(env.GitRootWin +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                            //                         archiveArtifacts artifacts: "BundleAssetCheckResult*Windows.txt,*.xml,*.svg", followSymlinks: false, allowEmptyArchive : true
                            //                     }
                            //                     Utility.CleanParallelJobWorkSpace(this)
                            //                 }
                            //             }
                            //         }
                            //         else{
                            //            Utility.MarkStageAsSkipped(STAGE_NAME)
                            //         }
                            //     }
                            // },
                            'Android':{
                                stage("Android"){
                                    if(params.Android == true && params.BuildClient == true ){
                                        node(BuildArgs.AssignedLabel_Android){
                                            try{
                                                overwriteBundleData("BundleDataAndroid.asset", 
                                                env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\Assets\\GameProject\\RuntimeAssets")
                                                dir(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                                                    deleteDir()
                                                }
                                                //gameClientSettings(BuildArgs, env.GitRootAndroid)
                                                def outputRoot = "${env.GitRootAndroid}\\${env.OutputFolderRoot}"
                                                stage('Build Android') {
                                                    if(params.BuildClient == true){
                                                        // clean previous build output
                                                        //def tmpOpt = env.Options.replace("AbDownload#0","AbDownload#1").replace("useAB#0","useAB#1")
                                                        dir(outputRoot){deleteDir()}
                                                        if(BuildArgs.GameConfig){
                                                            BuildArgs.GameConfig."${params.GameConfig}".each{source, dest ->l:{
                                                                writeFile(text:libraryResource(source), file:env.GitRootAndroid +"\\"+env.ProjectPathClient + "\\"+dest)
                                                                print readFile(env.GitRootAndroid +"\\"+env.ProjectPathClient + "\\"+dest)
                                                            }}
                                                        }
                                                        unityExec(unityExePath:env.UnityPathAndroid, projectPath:env.GitRootAndroid +"\\"+env.ProjectPathClient, 
                                                            executeMethod:env.ExecBuildAndroid, options:env.Options, output:"${outputRoot}", apk:"Android\\${env.ApkName}", 
                                                            logKeyWords:BuildArgs.UnityErrorCheck,
                                                            last_N_Line:2500, failedBuildIfNotFound:false, logToFile:true,checkOutput:env.ApkName,
                                                            buildAssetBundle:env.ExecBuildABAndroid)
                                                        def files =[]
                                                        try{
                                                            BuildArgs.GitPushBack.each{gitFolder, listOfPushItems ->l:{
                                                                dir(gitFolder){
                                                                    listOfPushItems.each{path, fileFilters->j:{
                                                                        if(fileFilters.size()==0){
                                                                            gitAddFile(path, params.Dryrun)
                                                                        }else{
                                                                             fileFilters.each{ ff-> k:{
                                                                                getFilePathByName(path, ff).each{
                                                                                    files.add(it)
                                                                                }
                                                                            }}
                                                                            gitAddFile(files, params.Dryrun)
                                                                        }
                                                                    }}
                                                                }
                                                                dir(gitFolder){
                                                                    gitCommit("Assets Built By Jenkins ${env.BUILD_URL}", params.Dryrun)
                                                                }
                                                            }}
                                                        }
                                                        catch(Exception ex){
                                                            // set unstable not fail the build
                                                            unstable(message: "Failed to submit built assets into Git")
                                                            print ex
                                                        }
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release Android") {
                                                    if(params.JustBuildAB != true){
                                                        if(psTestPath(env.GitRootAndroid +"\\"+env.ResaveFolder)){
                                                            compress(zip:"${outputRoot}\\Android\\Resave.zip" , path:env.GitRootAndroid +"\\"+env.ResaveFolder)
                                                        }
                                                        mdFive(path:outputRoot+"\\Android",relpath:"..")
                                                        clientBuildMD5 += readFile("${outputRoot}\\Android\\VerifyMd5.txt")
                                                        robocopy(outputRoot+"\\Android",env.OutputShare+"\\"+env.OutputShareClient+"\\Android"," /XF VerifyMd5.txt /E /S")
                                                        if(BuildArgs.ClientUploadFTP){
                                                            BuildArgs.ClientUploadFTP.each{ip, remote->l:{
                                                                withCredentials([usernamePassword(credentialsId: Utility.GetcredentialByDescription(ip), passwordVariable: 'pwd', usernameVariable: 'uid')]) {
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/android/${env.PackageName}", path:"${outputRoot}/Android/${env.ApkName}")
                                                                    ftpUpload(host:ip, "uid":uid, "pwd":pwd, remote:remote+"/android/${env.PackageName}", path:env.GitRootAndroid +"\\"+env.ResaveFolder)
                                                                }
                                                            }}
                                                        }
                                                    }
                                                    else{
                                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                                    }
                                                }
                                                stage("Release Android AB"){
                                                    def tmp = releaseAB(GitRoot:env.GitRootAndroid, OutputRoot: outputRoot)
                                                    clientAbMD5 += "${tmp}"
                                                    manifestVersion["AndroidBundleVersion"] = readFile(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\AssetBundles\\Android\\BundleDataVersion.txt").tokenize(",")[0]
                                                    if(BuildArgs.AbServerUploadFTP && params.UpdateHoxfix == true){ //env.Options.contains("AbDownload#1")
                                                        retry(2){
                                                            try{
                                                                timeout(30){
                                                                    uploadServerV2(path:env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\AssetBundles\\Android", 
                                                                        serverConfPath:BuildArgs.AbServerUploadFTP, serverTag:env.UpdateServerTag, port:12099, opt:"-pasv 1" )
                                                                }
                                                            }
                                                            catch(Exception e){
                                                                error 'FTP Timeout!'
                                                            }
                                                        }
                                                    } 
                                                }
                                            }
                                            finally{
                                                dir(env.GitRootAndroid +"\\"+env.ProjectPathClient+"\\ResourceCheckResult"){
                                                    archiveArtifacts artifacts: "BundleAssetCheckResult*.txt,*.xml,*.svg", followSymlinks: false, allowEmptyArchive : true
                                                }
                                                Utility.CleanParallelJobWorkSpace(this)
                                            }
                                        }
                                    }
                                    else
                                    {
                                        Utility.MarkStageAsSkipped(STAGE_NAME)
                                    }
                                }
                            },
                            failFast:env.FailFast == "true"?true:false
                        )
                    }
                }
            }
            stage("Release"){
                steps{
                    script{
                        node(BuildArgs.AssignedLabel){
                            if(clientBuildMD5 != ""){
                                writeFile(file:env.OutputShare+"\\"+env.OutputShareClient+"\\VerifyMd5.txt", text: clientBuildMD5)
                            }
                            compress(zip:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}.zip" , path:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}", relpath:".")
                            dir(env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}"){
                                deleteDir()
                            }
                            mdFive(file:env.OutputShare+"\\"+env.OutputShareAB+"\\manifest_${env.BUILD_NUMBER}.zip")
                            clientAbMD5 += env.md5 + " .\\manifest_${env.BUILD_NUMBER}.zip"
                            writeFile(file:env.OutputShare+"\\"+env.OutputShareAB+"\\VerifyMd5.txt", text: clientAbMD5, encoding:"UTF-8")
                            writeFile(file:env.OutputShare+"\\"+ env.OutputShareAB+"\\version.txt", text: Utility.ObjToJsonStr(manifestVersion,true))
                        }
                    }
                }
            }
        }
        post{
            always {
                script{
                    node(BuildArgs.AssignedLabel){
                        Utility.UpdateJobStatusOnELK(this)
                        Utility.AddJobResultOnELK(this)
                        Utility.CleanJobWorkSpace(this)
                        DingSender.SendDingMSG(this,env.DingRobot)
                        try{
                            EmailSender.SendEmail(this,env.MailTo,scmRS)
                        }
                        catch(Exception ex){
                            EmailSender.SendEmail(this,env.MailTo)
                        }
                    }
                    
                }
            }
        }
    }
}