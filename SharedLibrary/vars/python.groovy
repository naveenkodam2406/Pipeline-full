import lib.Utility
def call(pyFile, args, python="python", logToConsole=false){  // by default to use env python path
    if (python == null || python =="") python="python"
    def t = Utility.OS(this).Terminal
    def tmp = ""
    if(logToConsole == true){
        "${t}"(script:"${python} ${pyFile} ${args}", encoding:"utf-8")
    }
    else{
        tmp = "${t}"(script:"${python} ${pyFile} ${args}", returnStdout: true, encoding:"utf-8")
        def lines = tmp.tokenize("\r\n")
        println tmp
        for (int i = 0; i < lines.size();i++){
            properties = lines[i].tokenize('=')
            if (properties.size() <= 2 && properties.size() >= 1) {
                env."${lines[i].tokenize('=')[0]}" = lines[i].tokenize('=')[1]?lines[i].tokenize('=')[1]:""
            }
        }
    }
    
}