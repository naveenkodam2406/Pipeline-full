import lib.email.*
import lib.*
def call(ws, serverPackMap = [:], serverConf=[:]){
    dir(ws){
        if(serverPackMap){
            serverPackMap.each{ src, dest -> l:{
            robocopy(src, dest, "/MIR /S")
            }}
        }
        if(serverConf){
            serverConf.each{copyTo, FileSet->l:{ FileSet.each{file, fileConf -> j:{
                def fileName = file.tokenize("\\").last()
                def fileContent =  libraryResource( encoding: fileConf.encoding, resource:file)
                if(fileConf.Placeholder){
                    fileConf.Placeholder.each{k,v ->k:{
                        fileContent = fileContent.replace(Utility.RegExFind(fileContent,k),v.replace("{NODE_NAME}", env.NODE_NAME))
                    }}
                }
                writeFile(file: copyTo + "\\" + fileName, text:fileContent, encoding:fileConf.encoding)
            }}}}
        }
    }
        def build_name = "${env.JOB_NAME}.Server.${env.BUILD_NUMBER}.zip"
        compress(zip:build_name, path:ws, mode:"1",relpath:".")
        archiveArtifacts artifacts: "${build_name}", followSymlinks: false, onlyIfSuccessful: true 
        try{
            if(env.ELKURL){
                elasticQuery("addToElastic", """--url ${env.ELKURL} --data "{'opt':'server_build','type':'zip','job_name':'${env.JOB_NAME}','build_name':'${build_name}','build':'${BUILD_URL}artifact/${env.JOB_NAME}_server.${env.BUILD_NUMBER}.zip',${env.SCMStr} 'tag':'${env.BUILD_NUMBER}' }" """)
            }
        }
        catch(Exception ex){
            print("Failed when doing addToElastic, but not fail this build.")
            print ex
        }
        dir(ws){
        // cleanup
            deleteDir()
        }
    
}