package lib


class GitPipeline implements Serializable{
    static UserRemoteConfigs(agrsMap){
        if (agrsMap.Credential){
            agrsMap.UserRemoteConfigs.each{conf ->l:{
                conf["credentialsId"]= Utility.GetcredentialByDescription(agrsMap.Credential)
            }}
        }
        return agrsMap.UserRemoteConfigs
    }
    static ConfigSetup(script){
        def t = Utility.OS(script).Terminal
        script."${t}"(Utility.Git.GlobalConfQuotepath.CMD)
    }
    static CheckOutWithConfSetup(script, agrsMap, submodule=false, scmList = [:]){
        try{
            GitPipeline.ConfigSetup(script)
        }
        catch(Exception ex){
            print ex // if set conf is failed, don't fail the process
        }
        
        def scmEnv = null
        if(agrsMap.MultiScm){
            scmEnv = []
            agrsMap.MultiScm.each{
                agrsMap = agrsMap << it
                scmEnv.add(GitPipeline.CheckOut(script, agrsMap, agrsMap.submodule,scmList))
            }
        }else{
            scmEnv = GitPipeline.CheckOut(script, agrsMap, submodule,scmList)
        }
        
        return scmEnv

    }
    static CheckOut(script, agrsMap, submodule=false, scmList = [:]){
        //https://plugins.jenkins.io/git/
        // timeout unit: minutes
        // shallow: true -> only take the head one commit, without other commits' history, to improve checkout performance, 
        // but we use false for now.
        if(scmList){
            scmList.each{s ->l:{
                if(s.GIT_URL == agrsMap.UserRemoteConfigs[0]["url"]){
                    agrsMap.LocalBranch = s.GIT_LOCAL_BRANCH
                    agrsMap.Branches[0]["name"] = s.GIT_COMMIT
                }
            }}
        }
        def scmEnv = null
        def extentionMap = [ 
            // [$class: 'CleanCheckout'], 
            // with this the plugin will do the following steps, which somehow fails unity build at some point.
            //  Cleaning workspace
            //  git rev-parse --verify HEAD # timeout=10
            //  Resetting working tree
            //  git reset --hard # timeout=10
            //  git clean -fdx # timeout=10
            [$class: 'ScmName',name:agrsMap.UserRemoteConfigs[0]["url"]],
            [$class: 'CloneOption', timeout: 380,shallow: agrsMap["Shallow"]?:false],
            [$class: 'CheckoutOption', timeout: 380],
        ]
        if(agrsMap.LocalBranch){
            extentionMap.add([$class: 'LocalBranch',  localBranch: (agrsMap.LocalBranch) ])
        }else{
            //script.print(agrsMap)
            extentionMap.add([$class: 'LocalBranch',  localBranch: (agrsMap.Branches[0]["name"]) ])
        }
        if(agrsMap.RelativeTargetDir){
            extentionMap.add([$class: 'RelativeTargetDirectory', 
            relativeTargetDir: (agrsMap.RelativeTargetDir) ])
        }
        
        //https://javadoc.jenkins.io/plugin/git/hudson/plugins/git/extensions/impl/PathRestriction.html
        if(agrsMap.IncludedRegions || agrsMap.ExcludedRegions){
            extentionMap.add([$class: 'PathRestriction', 
            includedRegions: (agrsMap.IncludedRegions), excludedRegions: agrsMap.ExcludedRegions])
        }
        if(agrsMap.MessageExclusion){
            extentionMap.add([$class: 'MessageExclusion', 
            excludedMessage: (agrsMap.MessageExclusion)])
        }
        // https://issues.jenkins.io/browse/JENKINS-36195?page=com.atlassian.jira.plugin.system.issuetabpanels%3Aall-tabpanel
        if(agrsMap.UserExclusion){
            extentionMap.add([$class: 'UserExclusion', 
            excludedUsers: (agrsMap.UserExclusion)])
        }
        if(agrsMap.DisableRemotePoll){
            extentionMap.add([$class: 'DisableRemotePoll'])
        }
        scmEnv = script.checkout([
            $class: Utility.SCMClass.GitScm.getClassName(), 
            branches: agrsMap.Branches, 
            doGenerateSubmoduleConfigurations: false, 
            extensions: extentionMap, 
            submoduleCfg: [], 
            browser: [$class: 'GitLab', repoUrl: agrsMap.UserRemoteConfigs[0]["url"]],
            userRemoteConfigs: GitPipeline.UserRemoteConfigs(agrsMap)
        ])
        
        try{
            script.dir(scmEnv["GIT_CHECKOUT_DIR"]){
                script.gitBranch("--set-upstream-to=${scmEnv["GIT_BRANCH"]} ${scmEnv["GIT_LOCAL_BRANCH"]}")
            }
        }
        catch(Exception ex){
            print ex
        } 
        
        def submoduleScmEnv = []
        if(submodule == true){
            def submoduleConf  = null
            try{
                submoduleConf = (script.readFile(file:"./.gitmodules")).tokenize("\r\n")
            }
            catch(Exception ex){
                print "no submodule file"
            }
            if(submoduleConf != null){
                for (int i = 0; i< submoduleConf.size(); i++){
                    if(submoduleConf[i].startsWith("[submodule")){
                        i++
                        Map checkoutArgs =[:]
                        checkoutArgs["LocalBranch"] = agrsMap.LocalBranch
                        checkoutArgs["IncludedRegions"] = agrsMap.IncludedRegions
                        checkoutArgs["ExcludedRegions"] =  agrsMap.ExcludedRegions
                        checkoutArgs["UserExclusion"] = agrsMap.UserExclusion
                        checkoutArgs["MessageExclusion"] = agrsMap.MessageExclusion
                        checkoutArgs["DisableRemotePoll"] = agrsMap.DisableRemotePoll
                        checkoutArgs["Shallow"] = agrsMap["Shallow"]
                        checkoutArgs["ROOT_GIT_URL"] = agrsMap["ROOT_GIT_URL"]?:agrsMap.UserRemoteConfigs[0]["url"]
                        boolean skipped = false
                        if(agrsMap.SkippedSubmodules){
                            agrsMap.SkippedSubmodules.each{if(it == submoduleConf[i].split("=")[1].trim()) {skipped = true}}
                        }
                        if(skipped) continue
                        checkoutArgs["RelativeTargetDir"] = submoduleConf[i].split("=")[1].trim()
                        checkoutArgs["Credential"] = agrsMap.Credential
                        i++
                        
                        checkoutArgs["UserRemoteConfigs"] = [["url":
                            //submoduleConf[i].split("=")[1].trim()
                            Utility.UnifyGITURLForSubmodule(script, checkoutArgs["ROOT_GIT_URL"], submoduleConf[i].split("=")[1].trim())
                        ]]
                        i++
                        // try{        
                        //     checkoutArgs["Branches"] = [["name":submoduleConf[i].split("=")[1].trim()]]
                        //     checkoutArgs["LocalBranch"] = submoduleConf[i].split("=")[1].trim()

                        // }catch(Exception ex){
                        //     checkoutArgs["Branches"] = [["name":""]]
                        // }
                        checkoutArgs["Branches"] = [["name":submoduleConf[i].split("=")[1].trim()]]
                        checkoutArgs["LocalBranch"] = submoduleConf[i].split("=")[1].trim()

                        def tmpScm = GitPipeline.CheckOut(script, checkoutArgs, false, scmList)
                        submoduleScmEnv.add(tmpScm)
                    }
                }
            }
        }
        if(submoduleScmEnv.size() > 0){
            return [ScmEnv:scmEnv, Submodules:submoduleScmEnv]
        }else{
            return [ScmEnv:scmEnv]
        }
    }


}