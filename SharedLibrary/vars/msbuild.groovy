

import lib.Utility

def call(String options, String msbuild="msbuild.exe"){ //use env msbuild by default
    bat(msbuild + " -restore " + options)
} 


def call(Map kwargs=[:] ){
    def t = ""
    if(kwargs.shell){
        t = Utility.OS(this).Shell
    }else{
        t = Utility.OS(this).Terminal
    }
    if(kwargs.echo){
        t = "print"
    }
    if(kwargs.msbuild == null) kwargs.msbuild = """ "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\Community\\MSBuild\\Current\\Bin\\MSBuild.exe" """
    "${t}"("${kwargs.msbuild} ${kwargs.solution} -restore ${kwargs.opt}")
}