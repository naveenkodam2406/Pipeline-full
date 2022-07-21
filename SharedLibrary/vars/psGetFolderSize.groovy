def call(dir, unit="MB"){
    return powershell (script: "[float] (\"{0:N2}\" -f ((Get-ChildItem \"${dir}\" -Recurse | Measure-Object -Property Length -Sum -ErrorAction Stop).Sum / 1${unit})) | Out-String", 
                returnStdout: true).toFloat()
}