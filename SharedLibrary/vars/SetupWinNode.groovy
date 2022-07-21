import lib.*
import hudson.model.Node.Mode
import hudson.slaves.*
import jenkins.model.Jenkins

def call(BuildArgs) {
    pipeline{
        agent{label "Daniel"} // for test
        options {
            timestamps()
            skipDefaultCheckout true
            buildDiscarder(logRotator(daysToKeepStr: "7", numToKeepStr: "30"))
        }
        stages{
            stage("Setup Node"){
                when{expression { params.NodeName }}
                steps{
                    script{
                        Utility.SetEnvParams(this, params)
                        currentBuild.displayName += " - " + params.NodeName
                    }
                }
            }
            stage("Create Node"){
                steps{
                    script{
                         DumbSlave dumb = new DumbSlave(params.NodeName,  // Agent name, usually matches the host computer's machine name
                                    params.AgentLabels,           // Agent description
                                    params.HomeDir,                  // Workspace on the agent's computer
                                    "5",                          // Number of executors
                                    Mode.EXCLUSIVE,             // "Usage" field, EXCLUSIVE is "only tied to node", NORMAL is "any"
                                    params.AgentLabels,                         // Labels
                                    new JNLPLauncher(false),         // Launch strategy, JNLP is the Java Web Start setting services use
                                    RetentionStrategy.INSTANCE) // Is the "Availability" field and INSTANCE means "Always"

                            Jenkins.instance.addNode(dumb)
                            println "Agent ${params.NodeName} created with 1 executors and home ${params.HomeDir}"
                    }
                }
            }
            stage("Install Choco"){ // https://chocolatey.org/
                when{expression { params.InstallPackages }}
                steps{
                    script{
                        powershell("Set-Item WSMan:localhost\\client\\trustedhosts -value ${params.NodeName} -force")
                        psInstallChoco(params.NodeName, [username:params.Username,password:params.Password])
                    }
                }
            }
            stage("Install Packages"){
                when{expression { params.InstallPackages }}
                steps{
                    script{ // https://chocolatey.org/
                        if (BuildArgs.ChocoPkgs){
                            BuildArgs.ChocoPkgs.each{ pkg -> l:{
                                stage(pkg){
                                    try{
                                        remotePowerShell(params.NodeName, """\$env:path = \$env:Path +";\$env:ALLUSERSPROFILE\\chocolatey\\bin"; choco install ${pkg} -y""", [username:params.Username,password:params.Password])
                                    }
                                    catch(Exception ex){
                                        print(ex) // output ex, and go on
                                    }
                                }
                            }}
                        }
                    }
                }
            }
            stage("Run PS CMD"){
                when{expression { params.NodeName }}
                steps{
                    script{ // https://chocolatey.org/
                        if (BuildArgs.PSCommands){
                            BuildArgs.PSCommands.each{ cmd -> l:{
                                try{
                                    print ("Running... ${cmd}")
                                    remotePowerShell(params.NodeName, """${cmd}""", [username:params.Username,password:params.Password])
                                }
                                catch(Exception ex){
                                    print(ex) // output ex, and go on
                                }
                            }}
                        }
                    }
                }
            }
            stage("Run DownStreamJobs"){
                steps{
                    script{
                        if (BuildArgs.DownstreamJobs){
                            BuildArgs.DownstreamJobs.each{job ->j:{ job.each{ jn, pr ->k:{
                                def paramsList = []
                                pr.each{n,v ->l:{
                                    def pcombination = n.tokenize(".")
                                    paramsList.add("${pcombination[1]}"(name:pcombination[0],value:env."${v}" ?: ""))
                                    println ("${pcombination[1]}(name: ${pcombination[0]},value: "+env."${v}"?: ""+")")
                                }}
                                triggerBuild(jn , paramsList)
                            }}}}
                        }
                    }
                }
            }
        }
        post{
            cleanup {
                script{
                    Utility.CleanJobWorkSpace(this)
                }
            }
        }
    }
}