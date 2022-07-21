def call(BuildArgs){
    def platform = params.Platform 
    if(platform == null) platform = ""
    switch(platform.toLowerCase()){
        case ~/windows/:
            airTestWinPocoStage(BuildArgs)
        break;
        case ~/android/:
            airTestAndroidPocoStage(BuildArgs)
        break;
        default:
            airTestAndroidPocoStage(BuildArgs)
        break;
    }
}