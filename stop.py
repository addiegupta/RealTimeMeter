# Basic python script used to stop calls for Real Time Metering System
# Usage: python stop.py <num>
# where <num> is the number of calls to be stopped
# e.g. if num is 5, calls will be stopped for users from id 1 to id 5
# If num is not provided, a default of 1 call is stopped


import requests
import sys
url = 'http://127.0.0.2:8181/stop-call?id='

callnum = 1
if(len(sys.argv)==2):
    callnum = sys.argv[1]
 
for i in range(1,int(callnum) + 1):
    response = requests.get(url + str(i))
    #print(response.text)
print(str(callnum) +" calls stopped")
