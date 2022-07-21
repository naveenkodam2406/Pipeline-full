import java.text.SimpleDateFormat

def call(style="yyyy.MM.dd_HH.mm"){
    def date = new Date()
    //def sdf = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss")
    def sdf = new SimpleDateFormat(style)
    return sdf.format(date)
}