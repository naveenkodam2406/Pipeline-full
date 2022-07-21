def call(gitRoot){
    writeFile file:"excelConfAnalysis.bat", text: new String("""pushd ${gitRoot}\\GameProject\\Tools\\游戏配置分析导出工具\r\ncall ExcelConfigAnalysisTool.exe "-ExportData" || exit 1 """.getBytes("UTF-8"))
    def recode = bat(script: "excelConfAnalysis.bat", returnStatus: true)
    if(recode == 1){
        print readFile("${gitRoot}\\GameProject\\Tools\\游戏配置分析导出工具\\ExportAllConfigError.txt")
        error ("ExportAllConfigError")
    }
}