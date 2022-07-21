import requests, sys, os

url = sys.argv[0]
body =  sys.argv[1]   
res = requests.request('POST', url=url, data=body)
print (res)