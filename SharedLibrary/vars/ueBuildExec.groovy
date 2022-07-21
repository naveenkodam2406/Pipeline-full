import lib.Utility
def call(Map kwargs)
{
    try{
        bat (""" ${kwargs.runUATPath} BuildGraph -target="Make Installed Build Win64" -script=Engine/Build/InstalledEngineBuild.xml -set:GameConfigurations=Development -set:WithWin64=true -set:WithWin32=false -set:WithMac=false -set:WithAndroid=false -set:WithIOS=false -set:WithTVOS=false -set:WithLinux=false -set:WithLinuxAArch64=false -set:WithLumin=false -set:WithLuminMac=false -set:WithHoloLens=false -set:WithHTML5=false -set:WithPS4=false -set:WithXboxOne=false -set:WithDDC=false -set:HostPlatformDDCOnly=false -Clean >${kwargs.logfilepath}\\${kwargs.logFile} """)
    }catch(Exception ex){
        print ex
        error()
    }finally{
        def logdetailpath =  kwargs.logfilepath+"\\Engine\\Programs\\AutomationTool\\Saved\\Logs"
        def logdetailfile = "UBT-UnrealHeaderTool-Win64-Development_2.txt"
        dir(logdetailpath){
            archiveArtifacts artifacts: logdetailfile, followSymlinks: false, allowEmptyArchive : true
        }
        dir(kwargs.logfilepath){
            archiveArtifacts artifacts: kwargs.logFile, followSymlinks: false, allowEmptyArchive : true
            printLogFromFile(kwargs.logFile)
        }
    }
   
}