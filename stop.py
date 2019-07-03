import requests
import sys
url = 'http://127.0.0.2:8080/stop-call?id='

callnum = 100
if(len(sys.argv)==2):
    callnum = sys.argv[1]
 
for i in range(1,int(callnum) + 1):
    response = requests.get(url + str(i))
    #print(response.text)
print(str(callnum) +" calls stopped")
