/* args
 * --path %Path% --keepCount %KeepCount% --isShiftFolder %IsShiftFolder%
 *
 */
import lib.Utility
def call(path, keepCount, excluded=""){
    if( excluded == null ) excluded =""
    if (excluded != "") excluded = "--excluded ${excluded}"
    python(Utility.CopyGlobalLibraryScript(this, "scripts\\CleanFolder.py"), "--path ${path} --keepCount ${keepCount} ${excluded}")
}