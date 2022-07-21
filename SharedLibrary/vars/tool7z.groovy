def call(command, _zip="7z"){ // bydefault using env
    println "${_zip} ${command}"
    bat "${_zip} ${command}"
}