import lib.Utility
def call(FilePath, Window="activeWindow"){
    //activeWindow
    //screen, a screenshot of the entire desktop 
    def cl = Utility.CopyGlobalLibraryScript(this, "\\scripts\\TakeScreenShot.ps1") + """ -file "${FilePath}" -imagetype png -${Window} """
    powershell (script: cl)
}