# https://gist.github.com/cbittencourt/1608cd7c818afb81b34e4148a0f4f36f

param(
  [string]$jenkinsMasterbaseUrl,
  [string]$nodeName,
  [string]$privateKey,
  [string]$workspaceRootFolder
)

$ErrorActionPreference = "Stop"

Function Install-JenkinsBuildAgent {
  param(
    [string]$jenkinsMasterbaseUrl,
    [string]$nodeName,
    [string]$privateKey,
    [string]$workspaceRootFolder
  )

  # save current working directory
  Push-Location -StackName jenkinsBuildAgentInstall

  try {

      $jnlpUrl = "$jenkinsMasterbaseUrl/computer/$nodeName/slave-agent.jnlp"
      $jarUrl = "$jenkinsMasterbaseUrl/jnlpJars/agent.jar"

  $svc=Get-Service "jenkinsslave-${nodeName}" -ErrorAction SilentlyContinue

      if ($svc -ne $null) {
        Stop-Service $svc -ErrorAction SilentlyContinue
      }

      Write-Host " - Creating workspace" -ForegroundColor Green

      if ((Test-Path $workspaceRootFolder) -eq $False) {
        New-Item -Path $workspaceRootFolder -ItemType "Directory"
      }
      
      Set-Location $workspaceRootFolder

      Write-Host " - Downloading Jenkins files" -ForegroundColor Green
      
      Invoke-WebRequest $jarUrl -OutFile "agent.jar" -Verbose 
      #Invoke-WebRequest $jnlpUrl -OutFile "slave-agent.jnlp" -Verbose

      Write-Host " - Downloading WinSW (Jenkins Agent Service Wrapper)" -ForegroundColor Green
      
      Invoke-WebRequest "https://github.com/kohsuke/winsw/releases/download/winsw-v2.1.2/WinSW.NET4.exe" -OutFile "jenkins-slave.exe" -Verbose

      Write-Host " - Configuring Jenkins Agent Service" -ForegroundColor Green

      #jenkins-slave.exe.config
@'
  <!-- see http://support.microsoft.com/kb/936707 -->
  <configuration>
    <runtime>
      <generatePublisherEvidence enabled="false"/>
    </runtime>
    <startup>
      <supportedRuntime version="v4.0" />
      <supportedRuntime version="v2.0" />
    </startup>
  </configuration>
'@ | Out-File "jenkins-slave.exe.config" -Encoding Ascii

      #jenkins-slave.xml
@"
  <!--
  The MIT License
  Copyright (c) 2004-2017, Sun Microsystems, Inc., Kohsuke Kawaguchi, Oleg Nenashev and other contributors
  Permission is hereby granted, free of charge, to any person obtaining a copy
  of this software and associated documentation files (the "Software"), to deal
  in the Software without restriction, including without limitation the rights
  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  copies of the Software, and to permit persons to whom the Software is
  furnished to do so, subject to the following conditions:
  The above copyright notice and this permission notice shall be included in
  all copies or substantial portions of the Software.
  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
  THE SOFTWARE.
  -->
  <!--
    Windows service definition for Jenkins agent.
    This service is powered by the WinSW project: https://github.com/kohsuke/winsw/
    You can find more information about available options here: https://github.com/kohsuke/winsw/blob/master/doc/xmlConfigFile.md
    Configuration examples are available here: https://github.com/kohsuke/winsw/tree/master/examples
    To uninstall, run "jenkins-slave.exe stop" to stop the service, then "jenkins-slave.exe uninstall" to uninstall the service.
    Both commands don't produce any output if the execution is successful.
  -->
  <service>
    <id>jenkinsslave-${nodeName}</id>
    <name>Jenkins agent (jenkinsslave-${nodeName})</name>
    <description>This service runs an agent for Jenkins automation server.</description>
    <!--
      if you'd like to run Jenkins with a specific version of Java, specify a full path to java.exe.
      The following value assumes that you have java in your PATH.
    -->
    <!--<executable>C:\Program Files (x86)\Java\jre1.8.0_151\bin\java.exe</executable>-->
    <executable>${env:JAVA_HOME}bin\java.exe</executable>
    <arguments>-Xrs  -jar "%BASE%\agent.jar" -jnlpUrl ${jnlpUrl} -secret ${privateKey}</arguments>
    <!--
      interactive flag causes the empty black Java window to be displayed.
      I'm still debugging this.
    <interactive />
    -->
    <logmode>rotate</logmode>
    <onfailure action="restart" />
    
    <!--
      If uncommented, download the Remoting version provided by the Jenkins master.
      Enabling for HTTP implies security risks (e.g. replacement of JAR via DNS poisoning). Use on your own risk.
      NOTE: This option may fail to work correctly (e.g. if Jenkins is located behind HTTPS with untrusted certificate).
      In such case the old agent version will be used; you can replace agent.jar manually or to specify another download URL.
    -->
    <download from="${jarUrl}" to="%BASE%\agent.jar"/>
    
    <!-- 
      In the case WinSW gets terminated and leaks the process, we want to abort
      these runaway JAR processes on startup to prevent "Slave is already connected errors" (JENKINS-28492).
    -->
    <extensions>
      <!-- This is a sample configuration for the RunawayProcessKiller extension. -->
      <extension enabled="true" 
                className="winsw.Plugins.RunawayProcessKiller.RunawayProcessKillerExtension"
                id="killOnStartup">
        <pidfile>%BASE%\jenkins_agent.pid</pidfile>
        <stopTimeout>5000</stopTimeout>
        <stopParentFirst>false</stopParentFirst>
      </extension>
    </extensions>
    
    <!-- See referenced examples for more options -->
    
  </service>  
"@ | Out-File "jenkins-slave.xml" -Encoding Ascii

        
      if ($svc -eq $null) {
        Write-Host " - Installing Jenkins Agent Service" -ForegroundColor Green
        Invoke-Expression "./jenkins-slave.exe install"
        $svc=Get-Service "jenkinsslave-${nodeName}"
      }

      Write-Host " - Starting Jenkins Agent Service" -ForegroundColor Green
      Start-Service $svc

  }
  finally {
      # set location back to where we started
      Pop-Location -StackName jenkinsBuildAgentInstall
  }

}

choco install jdk8 -y

refreshenv

Install-JenkinsBuildAgent -jenkinsMasterbaseUrl $jenkinsMasterbaseUrl -nodeName $nodeName -privateKey $privateKey -workspaceRootFolder $workspaceRootFolder
