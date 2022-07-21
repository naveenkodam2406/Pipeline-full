import os
import datetime
import ast
import argparse
import qrcode

def GenQRCode(_Url, _Out):
    QRPath = _Out
    img = qrcode.make(_Url)
    with open(QRPath, "wb") as f:
        img.save(f)
    print ("QRPath={}".format(QRPath))

def _parserArgs_():
    parser = argparse.ArgumentParser(description='Generate QRCode from a given -url and save it into the given -out location'
                                     )
    # optional args for different usage
    parser.add_argument('--url', nargs='?', type=str, help='target url')

    parser.add_argument('--out', nargs='?', type=str, help="output location"
                        )
    return parser



if __name__ == '__main__':
    _args = _parserArgs_().parse_args()
    Out = _args.out
    Url = _args.url
    
    if Url is not None and Out is not None:
        GenQRCode(Url, Out)
    else:
        _parserArgs_().print_help()
        exit(-1)
