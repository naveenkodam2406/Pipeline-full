import lib.Utility

def call(QRContent, Output, ImgName){
python(Utility.CopyGlobalLibraryScript(this, "scripts\\GenQRCode.py"), "--url ${QRContent} --out ${Output}\\${ImgName}")
    dir(Output){
        archiveArtifacts artifacts: ImgName, followSymlinks: false, onlyIfSuccessful: true  
    }
    if(currentBuild.description != null){
        currentBuild.description += """<img width="150" height="150" src="${BUILD_URL}artifact/${ImgName}" alt="${QRContent}" target="_blank"/>"""
    }
    else{
        currentBuild.description = """<img width="150" height="150" src="${BUILD_URL}artifact/${ImgName}" alt="${QRContent}" target="_blank"/>"""
    }
    

}
