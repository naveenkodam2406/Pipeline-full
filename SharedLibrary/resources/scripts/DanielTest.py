 # -*- coding: utf-8 -*-
 # -*- encoding: utf-8 -*-
import os
import re
import shutil
import stat
import argparse
import subprocess
import time
# To keep a number of folders or builds for a input folder

notAFolder = "It is not a folder"
excluded = []
tagDel = "_del"


def exceptionQuitWithMessage(message):
    raise Exception(message)


def _filterFile_(_inPath, _keepCount):
    array = []
    for i in os.listdir(_inPath):
        found = False
        if excluded is not None:
            for regx in excluded:
                if re.search(regx, i) is not None:
                    found = True
            if found == False:
                array.append(i)
        else:
            array.append(i)
    arraySize = len(array)
    newArray = sorted([os.path.join(_inPath, i)
                       for i in array], key=os.path.getctime, reverse=True)
    return newArray[_keepCount: arraySize]


def _filterFileForShiftFolder_(_inPath, _keepCount):
    folderDict = dict()
    resultDict = dict()
    for i in os.listdir(_inPath):
        # print i
        if os.path.isfile(os.path.join(_inPath, i)):
            if re.search(r"[0-9]+ -", i) is not None:
                folderDict[os.path.join(_inPath, i.split(
                    " - ")[0])] = os.path.join(_inPath, i)  # shift log file
    folderSize = len(folderDict)
    # excluding the none existing folder, from the shift txt file, then time sorting
    newArray = sorted( filter(lambda x: os.path.exists(x) == True, [ i for i in folderDict.keys() ]),
                        key=os.path.getctime, reverse=True)
    for key in newArray[_keepCount:folderSize]:
        resultDict[key] = folderDict.get(key)
    return resultDict


def deleteFromPath(_inPath):
    newInPath = _inPath + tagDel
    fn = _inPath.split("\\")[-1]
    fn = fn + tagDel
    os.system("REN \"%s\" \"%s\" "%(_inPath, fn) )
    #using win console command to del the folder as the py one doesn't support too long path, failed at os.listdir()
    if os.path.isfile(newInPath):
        os.remove(newInPath)
    else:
        subprocess.call("RD "+ newInPath + "/S /Q", shell=True)
    #shutil.rmtree(newInPath, onerror=_removeReadOnly_)


def _removeReadOnly_(func, _path, __):
    if os.path.exists(_path):
        os.chmod(_path, stat.S_IWRITE)
        if os.path.isfile(_path):
            os.remove(_path)
        else:
            try:
                func(_path)
            except:
                os.system("RD %s /S /Q"%(_path) )


def _parserArgs_():
    parser = argparse.ArgumentParser(
        description='Delete folders by the given args')

    # parser.add_argument('--isShiftFolder', type=str2bool, default=False,
    #                     nargs="?", help='If the target folder is used by shift')
    parser.add_argument('--keepCount', required=True, nargs="?",
                        type=int, help='how many folders we want to keep, ordered by time')

    parser.add_argument('--path', required=True, nargs="?",
                        type=str, help="the folder path we are going to deal with")
    
    parser.add_argument('--backupPath', required=False, nargs="?",
                        type=str, help="the folder path we are going to deal with")
    
    parser.add_argument("--move", help = "Delete folder after backup, work with backupPath, default is False, won't delete.", action = 'store_true')
    
    parser.add_argument('--excluded', nargs="+",metavar='N', type=str,
                        help="something shouldn't be removed")
    return parser

def str2bool(v):
    if v.lower() in ('yes', 'true', 't', 'y', '1'):
        return True
    elif v.lower() in ('no', 'false', 'f', 'n', '0'):
        return False
    else:
        raise argparse.ArgumentTypeError('Boolean value expected.')

def _wrapBackSlash_(_rawString):
    if _rawString is not None:
        if re.search(r"\\\\", _rawString) is not None and  re.search(r"\\\\\\\\", _rawString) is None:
            _rawString = _rawString.replace("\\","\\\\")
        return _rawString
    else:
        return _rawString

def _stripBackSlash_(_rawString):
    if _rawString is not None:
        if re.search(r"\\\\\\\\", _rawString) is not None:
            _rawString = _rawString.replace("\\\\","\\")
            _rawString = _stripBackSlash_(_rawString)
        return _rawString
    else:
        return _rawString
def _copyFolder_(src, dest, rootSrc):
    if os.path.isdir(src):
        dest = src.replace(rootSrc,dest)
        result = subprocess.Popen("robocopy %s %s /E /S " %(src, dest))
        result.wait()
        return result.returncode
    else:
        os.system ("echo %s is not a folder, skipped" % src)
        return 35
    
if __name__ == '__main__':
    _args = _parserArgs_().parse_args()
    # isShiftFolder = _args.isShiftFolder
    inPath =_stripBackSlash_(_args.path)
    print (os.path.exists(_args.path))
    print (os.path.exists(inPath))
    if not os.path.exists(inPath):
        os.system("echo path not exists: "+ inPath)
        time.sleep(10)
        exit()
    keepCount = _args.keepCount
    excluded = _args.excluded
    deletedPath = ""
    backupPath = ""
    folderList = _filterFile_(inPath, keepCount)
    print (folderList)
    backup = (_args.backupPath is not None)
    for i in folderList:
        sourcePath = i
        if(backup):
            exitCode = _copyFolder_(sourcePath,_args.backupPath,inPath)
            # print (exitCode)
            if (exitCode < 8 and exitCode >= 0):
                backupPath += _wrapBackSlash_(sourcePath) + ","
                if(_args.move == True ):
                    deletedPath += _wrapBackSlash_(sourcePath) + ","
                    deleteFromPath(sourcePath)
        else:
            deletedPath += _wrapBackSlash_(sourcePath) + ","
            deleteFromPath(sourcePath)
    if not backupPath == "":
        print ("BackupPath=" + backupPath)
    if not deletedPath == "":
        print ("DeletedPath=" + deletedPath)