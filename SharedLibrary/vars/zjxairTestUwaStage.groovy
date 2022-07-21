def call(BuildArgs){
    def platform = params.Platform 
    if(platform == null) platform = ""
    switch(platform.toLowerCase()){
        case ~/windows/:
            zjxairTestWinUwaStage(BuildArgs)
        break;
        case ~/android/:
            airTestUwaStage(BuildArgs)
        break;
        default:
            airTestUwaStage(BuildArgs)
        break;
    }
}