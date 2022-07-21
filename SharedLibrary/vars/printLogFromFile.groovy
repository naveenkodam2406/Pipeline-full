import lib.Utility
def call(filePath){
    def ex = Utility.OS(this).Terminal
    def f = Utility.OS(this).Path(filePath)
    try{
        if(ex == "bat"){
            cmd = "type " + f
        }else if(ex == "sh"){
            cmd = "cat " + f
        }
        "${ex}"(cmd)
    }
    catch(Exception e){
        print("type log")
    }
    
}