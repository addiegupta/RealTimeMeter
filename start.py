import requests
url = 'http://127.0.0.2:8080/start-call/'

callnum = 2500
for i in range(1,callnum + 1):
    response = requests.get(url + str(i))
    #print(response.text)
print(callnum " calls started")