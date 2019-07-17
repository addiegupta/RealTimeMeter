# Basic python script used to start calls for Real Time Metering System
# Usage: python start.py <num>
# where <num> is the number of calls to be started
# e.g. if num is 5, calls will be started for users from id 1 to id 5
# If num is not provided, a default of 1 call is started

import requests
import sys
url = 'http://127.0.0.2:8181/start-call?id='

callnum = 1
if(len(sys.argv)==2):
    callnum = sys.argv[1]
 
for i in range(1,int(callnum) + 1):
    response = requests.get(url + str(i))
    #print(response.text)
print(str(callnum) +" calls started")
