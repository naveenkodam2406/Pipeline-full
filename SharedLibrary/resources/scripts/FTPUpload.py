from ftplib import FTP
import os
import sys
import time
import argparse


def cwdirs(currentDir,ftpobject):
    if currentDir != "":
        try:
            ftpobject.cwd(currentDir)
        except:
            cwdirs("/".join(currentDir.split("/")[:-1]),ftpobject)
            ftpobject.mkd(currentDir)
            ftpobject.cwd(currentDir)

def upload_dir(localFilePath, remoteDir, _args, ftpobject):
    totalBytes = 0
    if not remoteDir is None:
        cwdirs(remoteDir, ftpobject)
    else:
        remoteDir=""
    if os.path.isdir(localFilePath):
        remoteSubDir = os.path.basename(localFilePath)
        remoteDir = remoteDir + "/" + remoteSubDir
        if remoteSubDir in ftpobject.nlst():
            if not _args.overwrite:
                datetimestr = time.strftime("%Y%m%d%H%M%S", time.localtime(time.time()))
                ftpobject.rename(remoteSubDir, remoteSubDir + ".bak." + datetimestr)
                ftpobject.mkd(remoteSubDir)
            else:
                os.system("echo Overwrite Remote Directory: "+ remoteDir)
        else:
            ftpobject.mkd(remoteSubDir)
            os.system("echo  Create Remote Directory: "+ remoteDir)
        ftpobject.cwd(remoteDir)        
        for dirfile in os.listdir(localFilePath):
            apath = os.path.join(localFilePath, dirfile)
            totalBytes += upload_dir(apath, remoteDir, _args, ftpobject)
    else:
        basef = os.path.basename(localFilePath)
        if basef in ftpobject.nlst():
            if not _args.overwrite:
                datetimestr = time.strftime("%Y%m%d%H%M%S", time.localtime(time.time()))
                ftpobject.rename(basef, basef + ".bak." + datetimestr)
            else:
                os.system("echo Overwrite Remote File: " + basef)
        os.system("echo File Upload Start: " + localFilePath)
        with open(localFilePath, 'rb') as file:
            ftpobject.storbinary('STOR %s' % basef, file)
            #ftpobject.rename(basef+".wip", basef)
            totalBytes = os.path.getsize(localFilePath)
            os.system("echo File Upload OK: "+ str(totalBytes) + " bytes for " + localFilePath)
    return totalBytes

def _parserArgs_():
    parser = argparse.ArgumentParser(
        description='***** BlackJack ftp backup utility usage help doc *****')
    parser.add_argument("-r", "--remote", dest = 'remote', help = "remote Dir", required = False,)
    parser.add_argument("-port", "--port", dest = 'port', help = "ftp port",  type=int, required = False,)
    parser.add_argument("-pasv", "--pasv", dest = 'pasv', help = "ftp pasv mode",  type=int, required = False,)
    parser.add_argument("-p", "--path", dest = 'path', help = "path of a file or a dir", required = True,)
    parser.add_argument("-ipv6", "--ipv6", dest = 'ipv6', help = "socket.AF_INET6 enable or not", action = 'store_true')
    parser.add_argument("-o", "--overwrite", dest = 'overwrite', help = "overwrite if files already exist in remote", action = 'store_false')
    parser.add_argument("-host", "--host", dest = 'host', help = "host to upload")
    parser.add_argument("-uid", "--username", dest = 'username', help = "username to login")
    parser.add_argument("-pwd", "--password", dest = 'password', help = "password to login")
    parser.add_argument("-enc", "--encoding", dest = 'encoding', help = "ftp encoding")
    return parser

def ftpSession(args):
    ftpobject = FTP()
    ftpobject.connect(_args.host, port)
    ftpobject.login(_args.username, _args.password)
    ftpobject.set_pasv(pasv)
    if args.ipv6:
        import socket
        ftpobject.af = socket.AF_INET6
        print("ipv6")
    ftpobject.encoding='GB2312'
    if args.encoding:
        ftpobject.encoding = args.encoding
    return ftpobject

port = 21
pasv = 0
username = "anonymous"
password = "anonymous"

if __name__ == '__main__':
    _args = _parserArgs_().parse_args()
    
    print(_args.ipv6)        
    if not _args.port is None:
            port = _args.port
    if not _args.pasv is None:
            pasv = _args.pasv
    ftpOjb = ftpSession(_args)
    if not os.path.exists(_args.path):
        os.system("echo file or dir does not exist: " + _args.path)
        sys.exit(0)
    totalBytes = upload_dir(_args.path, _args.remote, _args,ftpOjb)
    ftpOjb.quit()
    os.system('echo Backup Finished, total bytes: ' + str(totalBytes))