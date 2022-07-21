import lib.*
def call(BuildArgs){
    pipeline{ 
            agent {label "${params.agenthost}"}
            options {
                timestamps()
                timeout(time: 12, unit: 'HOURS')
                skipDefaultCheckout true
                buildDiscarder(logRotator(numToKeepStr: "20"))
            }
            stages{      
                stage("svn checkout"){
                    steps{
                        script{
                            while(true){
                                try{

writeFile file:"test.ps1", text:"""
                            \$throttleLimit = ${params.number}
\$SessionState = [system.management.automation.runspaces.initialsessionstate]::CreateDefault()
\$Pool = [runspacefactory]::CreateRunspacePool(1, \$throttleLimit, \$SessionState, \$Host)
\$Pool.Open()

\$ScriptBlock = {
    \$commit_file="commit_testfile.zip"
	\$url="\$pwd"
    \$workspacepath="\$pwd\\.."
	\$repository="http://192.168.5.56/svn/pressureTest"
    echo "1"
#SVN_Checkoout
	\$mystr=Get-Random
	echo "\$mystr"
	svn checkout \$repository --username=admin --password=admin \$url\\\$mystr
#SVN_Update
	svn checkout \$repository "\$url\\test2" --username=admin --password=admin
    copy "\$workspacepath\\\$commit_file" "\$url\\test2"
    cd "\$url\\test2"
    svn add *
    svn commit  -m "测试update"
    cd "\$url"
	svn update "\$url\$mystr"
#SVN_Commit
	svn copy \$repository "\$repository/testbranch\$mystr" -m 'make branch testbranch\$mystr'
	svn checkout "\$repository/testbranch\$mystr"
	copy "\$workspacepath\\\$commit_file" "\$url\\testbranch\$mystr"
	echo "------------------------------------------------------"
	cd "\$url\\testbranch\$mystr"
	svn add *
	svn commit -m "testbranch\$mystr"
	svn rm \$repository/testbranch\$mystr -m "delete testbranch\$mystr"
}
\$threads = @()
\$handles = for (\$x = 1; \$x -le \$throttleLimit; \$x++) {
    \$powershell = [powershell]::Create().AddScript(\$ScriptBlock)
    \$powershell.RunspacePool = \$Pool
    \$powershell.BeginInvoke()
    \$threads += \$powershell
}

do {
  \$i = 0
  \$done = \$true
  foreach (\$handle in \$handles) {
    if (\$handle -ne \$null) {
      if (\$handle.IsCompleted) {
        \$threads[\$i].EndInvoke(\$handle)
        \$threads[\$i].Dispose()
        \$handles[\$i] = \$null
      } else {
        \$done = \$false
      }
    }
    \$i++
  }
  if (-not \$done) { Start-Sleep -Milliseconds 500 }
} until (\$done)
                            """
                            bat("""PowerShell set-executionpolicy remotesigned""")
                            bat("""PowerShell ./test.ps1""")

                                }
                                finally{
                                    Utility.CleanJobWorkSpace(this)      
                                }
                            }
                            
                        }
                    }
                } 
                // stage("svn update"){
                //     steps{
                //         script{
                //             writeFile file:"update.sh",text: """
                //             echo "-----------------------添加远程仓库文件---------------------------------------"
                //             svn checkout https://zengjianxiong-pc:400/svn/test2/ "C:\\Users\\zengjianxiong\\Desktop\\script_SVN\\test2" --username=zjx --password=123456
                //             cp -rf "C:\\Users\\zengjianxiong\\Desktop\\script_SVN\\BuildGitExtension.bat" "C:\\Users\\zengjianxiong\\Desktop\\script_SVN\\test2"
                //             cd "C:\\Users\\zengjianxiong\\Desktop\\script_SVN\\test2"
                //             svn add *
                //             svn commit  -m "测试update"
                //             echo "-----------------------添加完成，准备提交-------------------------------------"
                //             echo "-----------------------开始update---------------------------------------------"
                //             cd "C:\\Users\\zengjianxiong\\Desktop\\script_SVN"
                //             for file in `ls -d */`
                //             do
                //             {
                //              echo -e \$file
	            //              svn update \$file
                //              }&
                //             done
                //             wait
                //             update_time_s=`date +%s`
                //             sum_update_time=\$[ \$update_time_s - \$checkout_time_s ]
                //             echo "----------------------svn update耗时"\$((sum_update_time))"s--------------------"
                            
                //             """
                //             bat(script:"""update.sh""")
                //         }
                //     }
                // } 
                // stage("svn commit"){
                //     steps {
                //         script{
                //             writeFile file:"commit.sh", text: """
                //             echo "----------------------更新多个本地仓库文件-------------------------------------"
                //             echo "----------------------开始Commit------------------------"
                //             echo "----------------------修改多个本地仓库文件-------------------------------------"
                //             for i in `seq 1 ${params.number}`
                //             do
                //             {
                //                 svn copy https://zengjianxiong-pc:400/svn/test2 "https://zengjianxiong-pc:400/svn/test2/testbranch\${i}" -m 'make branch testbranch\${i}'
                //                 svn checkout "https://zengjianxiong-pc:400/svn/test2/testbranch\${i}"
                //                 cp -rf "C:\\Users\\zengjianxiong\\Desktop\\script_SVN\\GitExtension.bat" "C:\\Users\\zengjianxiong\\Desktop\\script_SVN\\testbranch\${i}"
                //                 echo "------------------------------------------------------"
                //                 cd "C:\\Users\\zengjianxiong\\Desktop\\script_SVN\\testbranch\${i}"
                //                 svn add *
                //                 svn commit -m "testbranch\${i}"
                //             }&
                //             done
                //             wait
                //             commit_time_s=`date +%s`
                //             sum_commit_time=\$[ \$commit_time_s - \$update_time_s ]
                //             echo "----------------------svn commit耗时"\$((sum_commit_time))"s--------------------"                           
                //             """
                //             bat(script:"""commit.sh""")
                //         }
                //     }
                // }             
               
            }
        }
}