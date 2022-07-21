def call(Node, Command){
    remotePowerShell(Node, """\$env:path = \$env:Path +";\$env:ALLUSERSPROFILE\\chocolatey\\bin"; ${Command}""")
}