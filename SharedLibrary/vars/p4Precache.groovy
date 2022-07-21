def call(p4Port,p4User,p4Client,p4Path="..."){
    try{
        bat("p4 -p ${p4Port} -u ${p4User} -c ${p4Client} -Zproxyload sync ${p4Path}")
    }
    catch (Exception ex){
        //catch the ex and not fail the build
        print ex
    }
}