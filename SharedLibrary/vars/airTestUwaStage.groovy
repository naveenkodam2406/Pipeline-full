def call(BuildArgs){
    def platform = params.Platform 
    if(platform == null) platform = ""
    switch(platform.toLowerCase()){
        case ~/windows/:
            airTestWinUwaStage(BuildArgs)
        break;
        case ~/android/:
            airTestAndroidUwaStage(BuildArgs)
        break;
        default:
            airTestAndroidUwaStage(BuildArgs)
        break;
    }
} 