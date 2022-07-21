import hashlib
import io
import os
import argparse
import pathlib 

def generate_md5(filename):
    m = hashlib.md5()
    file = io.FileIO(filename, 'r')
    bytes = file.read(1024)
    while bytes != b'':
        m.update(bytes)
        bytes = file.read(1024)
    file.close()
    return m.hexdigest()

def process_md5(verify_paths, _relpath):
    md5List = []
    for verify_item in verify_paths:
        replace_path = verify_item
        if(_relpath == ".."):
            replace_path = str(pathlib.Path(verify_item).parent)
            print (replace_path)
        if os.path.exists(verify_item):
            server_version = []
            for root_item, dirs_item, files_item in os.walk(verify_item):
                for file_name in files_item:
                    full_file = os.path.join(root_item, file_name)
                    server_version.append(
                        generate_md5(full_file) + " " + full_file.replace(replace_path, ".").replace("/","\\") + '\n')
            md5Path = os.path.join(verify_item, "VerifyMd5.txt")
            md5List.append(md5Path)
            version_content = open(md5Path, "w+")
            for item in server_version:
                version_content.writelines(item)
            version_content.close()
    print ("md5List={}".format(",".join(md5List)))

def _parserArgs_():
    parser = argparse.ArgumentParser(
        description='calculate md5 for the folder, and write VerifyMd5.txt to the folder')

    parser.add_argument('--path', required=False, nargs="+",metavar='N',
                        type=str, help="--path AAAAA [BBBB CCCCC]")
    parser.add_argument('--relpath', required=False, default=".", 
                        type=str, help="--Relpath .. , by default use ., .. start with parent path, . start with current path")
    parser.add_argument('--file', required=False, default=".", 
                        type=str, help="if a file is given the func will return one md5")
    return parser


if __name__ == '__main__':
    _args = _parserArgs_().parse_args()
    if(_args.path):
        process_md5(_args.path,_args.relpath)
    elif(_args.file):
        print ("md5={}".format(generate_md5(_args.file)))