def call(path, type){
    if(!isUnix()){
        psGetFileNameByType(path, type)
    }
    else{
        print "To do Unix getFileNameByType"
    }
}
