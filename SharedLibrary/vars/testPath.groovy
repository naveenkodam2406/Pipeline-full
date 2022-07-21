import lib.Utility

def call(path){
    def os = Utility.OS(this)
    if (os == Utility.MobilePlatform.WINDOWS){
        return psTestPath(path)
    }
    else if (os == Utility.MobilePlatform.UNIX){
        return shTestPath(path)
    }
}