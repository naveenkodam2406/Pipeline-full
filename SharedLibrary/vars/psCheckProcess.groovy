def call(processName){
    return powershell(script:"""[int] ("{0}" -f ((Get-WmiObject Win32_Process -Filter "name like '%${processName}%'" | Measure-Object -Property WorkingSetSize -Sum -ErrorAction Stop).Sum / 1MB))""", returnStdout: true).toInteger()
    // return the workingsizeset of the process, the number greater than the memory usage from task manager 
}