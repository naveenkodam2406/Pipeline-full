import os
from posixpath import relpath
import zipfile
import argparse
import pathlib 


    
def zipdir(path, ziph, relpath):
    # ziph is zipfile handle
    for root, dirs, files in os.walk(path):
        for file in files:
            ziph.write(os.path.join(root, file), 
                       os.path.relpath(os.path.join(root, file), 
                                       os.path.join(path, relpath))) # .. -> path/stuff[...], .-> stuff[...] 
      



def _parserArgs_():
    parser = argparse.ArgumentParser(
        description='Delete folders by the given args')

    parser.add_argument('--zip', type=str, default=False,
                        nargs="?", help='If the target folder is used by shift')
    parser.add_argument('--path', required=True, nargs="?",
                        type=str, help="the folder path we are going to deal with")
    parser.add_argument('--relpath', required=False, nargs="?", default=".",
                        type=str, help=".. -> path/stuff[...], .-> stuff[...] ")
    parser.add_argument('--mode', nargs="?", type=int,required=False,
                        help="0:zipfile.ZIP_STORED, 1:zipfile.ZIP_DEFLATED,2:zipfile.ZIP_BZIP2,3:zipfile.ZIP_LZMA")
    return parser


if __name__ == '__main__':
    _args = _parserArgs_().parse_args()
    zipMode = None
    rp = _args.relpath
    if(_args.mode == 0): zipMode = zipfile.ZIP_STORED
    elif (_args.mode == 1): zipMode = zipfile.ZIP_DEFLATED
    elif (_args.mode == 2): zipMode = zipfile.ZIP_BZIP2
    elif (_args.mode == 3): zipMode = zipfile.ZIP_LZMA
    
    if (zipMode == None): zipMode = zipfile.ZIP_STORED
    os.system("echo zip Mode: %s" % (zipMode))
    os.system("echo zip Folder: %s" % (_args.path))
    os.system("echo zip File: %s" % (_args.zip))
    target_path = pathlib.Path(_args.zip).parent
    target_path.mkdir(parents=True, exist_ok=True)
    
    zipf = zipfile.ZipFile(_args.zip+".compressWIP", 'w', zipMode)
    zipdir(_args.path, zipf, rp)
    zipf.close()
    if os.path.exists(_args.zip): os.remove(_args.zip)
    os.rename(_args.zip+".compressWIP", _args.zip)