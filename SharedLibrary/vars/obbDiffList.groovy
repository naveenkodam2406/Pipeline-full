import lib.Utility
def call(kwargs = [:]){
    kwargs.out = Utility.OS(this).Path(kwargs.out)
    kwargs.fullApk = Utility.OS(this).Path(kwargs.fullApk)
    kwargs.obbApk = Utility.OS(this).Path(kwargs.obbApk)
    kwargs.resave = Utility.OS(this).Path(kwargs.resave)
    kwargs.exclude = Utility.OS(this).Path(kwargs.exclude)
    python(Utility.CopyGlobalLibraryScript(this, "scripts/Extract.py"), "-obbDiff --fullApk ${kwargs.fullApk} --obbApk ${kwargs.obbApk} --resave ${kwargs.resave} --exclude ${kwargs.exclude} --out ${kwargs.out}", kwargs.python)
}