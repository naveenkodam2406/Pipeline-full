import urllib.request, urllib.error, urllib.parse
import urllib.request, urllib.parse, urllib.error
import json
import os
import datetime
import ast
import argparse
import re

def _addIntoElastic_(_queryUrl, _queryData):
    urllib.request.build_opener(urllib.request.HTTPHandler)
    _queryUrl = _queryUrl if _queryUrl.endswith("/") else _queryUrl + "/"
    request = urllib.request.Request(_queryUrl, _queryData.encode('UTF-8'))
    request.add_header('Content-Type', 'application/json')
    request.get_method = lambda: 'POST'
    response = urllib.request.urlopen(request)
    response.close()
    return None

def _addIntoAutoTest_(_queryUrl, _queryData):
    urllib.request.build_opener(urllib.request.HTTPHandler)
    _queryUrl = _queryUrl if _queryUrl.endswith("/") else _queryUrl + "/"
    request = urllib.request.Request(_queryUrl, _queryData.encode('UTF-8'))
    request.add_header('Content-Type', 'application/json')
    request.get_method = lambda: 'POST'
    f = urllib.request.urlopen(request)
    response = f.read()
    f.close()
    if response != None:#only update existing record.
        rd = json.loads(response)
        print ("AutoTestInfo=" + rd["extraInfo"]) 

def _addIntoElasticById_(_queryUrl, _queryData, _id):
    urllib.request.build_opener(urllib.request.HTTPHandler)
    _queryUrl = _queryUrl if _queryUrl.endswith("/") else _queryUrl + "/"
    # return None
    if _id == "":
        raise Exception("ID is required.")
    request = urllib.request.Request(_queryUrl + urllib.parse.quote_plus(_id), _queryData.encode('UTF-8'))
    request.add_header('Content-Type', 'application/json')
    request.get_method = lambda: 'PUT'
    response = urllib.request.urlopen(request)
    response.close()
    return None

def getElasticById(_queryUrl,_id):   
    _queryUrl = _queryUrl if _queryUrl.endswith("/") else _queryUrl + "/"
    try:
        urllib.request.build_opener(urllib.request.HTTPHandler)
        request = urllib.request.Request(_queryUrl + urllib.parse.quote_plus(_id))
        request.add_header('Content-Type', 'application/json')
        request.get_method = lambda: 'GET'
        f = urllib.request.urlopen(request)
        response = f.read()
        f.close()
        return response
    except urllib.error.HTTPError as e:
        if e.getcode()==404:
            return e
        else:
            raise(e)

def updateElasticByID(_queryUrl,_queryData,_id):
    oldData = getElasticById(_queryUrl,_id)
    if oldData != None:#only update existing record.
        oldData = json.loads(oldData)
        oldDataDict = oldData["_source"] #only update entity
        newDataDict = json.loads(_queryData)
        for key, value in newDataDict.items():
            oldDataDict[key] = value #update/create key value pairs from new data
        _addIntoElasticById_(_queryUrl,json.dumps(oldDataDict),_id)

def _jsonToProperty_(jsonObj):
    for key, value in jsonObj.items():
        try:
            if key != "sort" and value is not None:
                # just try to find is there are json objs in the value
                _jsonToProperty_(value)
        except:  # if in side a value there is no more json objects, print the key value
            if str(value).find("\\") != -1:
                # double the backward slash for groovy
                value = value.replace("\\", "\\\\")
            print("{}={}".format(key, value))        

def addEntity(_queryUrl, _queryData, _id=None):
    data = json.loads(_queryData)
    if data.get("@timestamp") is None:
        data['@timestamp'] = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    _queryData = json.dumps(data)
    if _id is None:
        _addIntoElastic_(_queryUrl, _queryData)
    else:
        _addIntoElasticById_(_queryUrl, _queryData, _id)

def addAutoTest(_queryUrl, _queryData):
    data = json.loads(_queryData)
    if data.get("@timestamp") is None:
        data['@timestamp'] = datetime.datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    _queryData = json.dumps(data)
    _addIntoAutoTest_(_queryUrl, _queryData)

def _parserArgs_():
    parser = argparse.ArgumentParser(description='get/add build info from Elastic, with provided json data, '
                                     + 'with folder scanning function to return the largest CL folder')
    # positional arg for calling function
    parser.add_argument(  # make this optional, therefore it can print help if we don't call a proper action.
        'method', type=str, nargs='?', help='action to call, addToElastic,getFromElastic, getElasticById,updateElasticByID')
    # optional args for different usage
    parser.add_argument('--url', nargs='?', type=str, help='Elastic url')
    parser.add_argument('--data', nargs='?', type=ast.literal_eval,  # convert to dictionary
                        help="json data for Elastic, single quote for string" )
    parser.add_argument('--id', nargs='?', type=str,
                        help="predefined document id, used for some Elastic category" )
    parser.add_argument('--root', nargs='?', type=str,
                        help="root for directoryFind" )
    parser.add_argument('--folder', nargs='?', type=str,
                        help="folder for directoryFind to look for" )
    parser.add_argument('--k', nargs='?', type=str,
                        help="folder for directoryFind to look for" )

    return parser

def directoryFinder(folder, k, root='.'):
    ret = ""
    for path, dirs, __ in os.walk(root):
        if folder in dirs:
            ret += os.path.join(path, folder) + " "
            break
    print("{}={}".format(k, ret))


if __name__ == '__main__':
    _args = _parserArgs_().parse_args()
    method = _args.method
    Data = json.dumps(_args.data)
    Url = _args.url
    _id = _args.id
    folder = _args.folder
    root = _args.root
    k = _args.k
    if _id is not None:
        _id = _id.lower()
    if method == 'addToElastic':
        addEntity(Url, Data, _id)
    elif method == 'addToAutoTest':
        addAutoTest(Url, Data)
    elif method == 'updateElasticByID':
        updateElasticByID(Url, Data, _id)
    elif method == 'directoryFinder':
        directoryFinder(folder, k, root)
    else:
        _parserArgs_().print_help()
        exit(-1)