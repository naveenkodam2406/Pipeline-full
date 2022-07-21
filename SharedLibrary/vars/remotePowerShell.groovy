def call(hostName, script, userpass=null){
    if (userpass){
        powershell """\$password = "${userpass.password}"|ConvertTo-SecureString -AsPlainText -Force
        |\$credentials = New-Object System.Management.Automation.PSCredential("${userpass.username}" ,\$password)
        |invoke-command -Cred \$credentials  -ComputerName ${hostName} -ScriptBlock { ${script} }""".stripMargin()
    }
    else{
        powershell "invoke-command -ComputerName ${hostName} -ScriptBlock { ${script} }"
    }
}