def call(path, ignoreFailure=true){
    retry(2){
        try{
            dir(path){
                deleteDir()
            }
        }
        catch(Exception ex){
            if(!ignoreFailure){
                throw ex
            }
            else{
                print "Failed to delete ${path}, ignore this time."
            }
        }
    }
}