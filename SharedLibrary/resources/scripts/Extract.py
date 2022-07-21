import os
from zipfile import ZipFile
import argparse

def fileList(zipPath):
    with ZipFile(zipPath) as zipObj:
        return zipObj.namelist()

def obbDiff(fullApk, obbApk, resave, exclude, out):
    fullSet    = fileList(fullApk)
    obbSet     = fileList(obbApk)
    resaveSet = ["assets/Resave/" + str(x) for x in fileList(resave) ]
    diffList = list((set(fullSet) ^ set(obbSet)) - set(exclude))
    finalList = list(set(resaveSet) - set(exclude))
    diffList.sort(reverse=False)
    finalList.sort(reverse=False)
    diffList += finalList
    OBB_APK_Diff = os.path.join(out)
   
    version_content = open(OBB_APK_Diff, "w+")
    version_content.write("\n".join(diffList))
    version_content.close()
    
def _parserArgs_():
    parser = argparse.ArgumentParser(
        description='')

    parser.add_argument('-obbDiff', action='store_true')

    parser.add_argument('--obbApk', required=False, nargs="?",
                        type=str, help="")
    parser.add_argument('--fullApk', required=False, nargs="?",
                        type=str, help="")
    parser.add_argument('--resave', required=False, nargs="?",
                        type=str, help="")
    parser.add_argument('--exclude', required=False, nargs="+",metavar='N',
                        type=str, help="")
    parser.add_argument('--out', required=False, nargs="?",
                        type=str, help="")
    return parser

if __name__ == '__main__':
    _args = _parserArgs_().parse_args()
    if (_args.obbDiff):
        obbDiff(_args.fullApk,_args.obbApk, _args.resave, _args.exclude, _args.out)