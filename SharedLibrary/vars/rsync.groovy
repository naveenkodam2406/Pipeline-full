def call(source, destination, include="", exclude=""){
    def opt = ""
    include.split(" ").each{
        if(it != ""){
            opt += """--include "${it}" """
        }
        
    }
    exclude.split(" ").each{
        if(it != ""){
            opt += """--exclude "${it}" """
        }
    }
    sh("""rsync -r ${opt} ${source} ${destination}""")
}