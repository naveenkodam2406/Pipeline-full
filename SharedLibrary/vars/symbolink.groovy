def call(source, destination){
    bat("""if exist "${destination}" rmdir "${destination}" /q /s""")
    bat(""" MKLINK /J "${destination}" "${source}" """)
}