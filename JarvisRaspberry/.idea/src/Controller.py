import time
import requests, json, hashlib, uuid, time

from Device import devices
from Device import Device

# This is where to insert your generated API keys (http://api.telldus.com/keys)
pubkey = "FEHUVEW84RAFR5SP22RABURUPHAFRUNU"  # Public Key
privkey = "ZUXEVEGA9USTAZEWRETHAQUBUR69U6EF"  # Private Key
token = "a105fc203e00fd0d0958db3bace92026061b226ad"  # Token
secret = "b2368173e0fc4d83b8ffcc946fce97aa"  # Token Secret

localtime = time.localtime(time.time())
timestamp = str(time.mktime(localtime))
nonce = uuid.uuid4().hex
oauthSignature = (privkey + "%26" + secret)

#Find device and execute an action based on the command
def control(command):
    print("Command = "+ command)
    if (command == "turn on lamp"):
        print("Command found: turn on lamp")
        if 'FloorLamp' in devices:
            print("floorlamp found!")
            action("device/turnOn", devices.get("FloorLamp"))
        else:
            print("no command")
    elif (command == "turn off lamp"):
        print("Command found: turn off lamp")
        if 'FloorLamp' in devices:
            print("floorlamp found!")
            action("device/turnOff", devices.get("FloorLamp"))
        else:
            print("no command")
    elif (command == "status"):
        print("status")
        status()
    else:
        print("Else")

def action(command, id):
    # GET-request
    response = requests.get(
        url="https://pa-api.telldus.com/json/" + command,
        params={
            "id": id,
        },
        headers={
            "Authorization": 'OAuth oauth_consumer_key="{pubkey}", oauth_nonce="{nonce}", oauth_signature="{oauthSignature}", oauth_signature_method="PLAINTEXT", oauth_timestamp="{timestamp}", oauth_token="{token}", oauth_version="1.0"'.format(
                pubkey=pubkey, nonce=nonce, oauthSignature=oauthSignature, timestamp=timestamp, token=token),
        },
    )
    # Output/response from GET-request
    responseData = response.json()
    print(json.dumps(responseData, indent=4, sort_keys=True))

def status(): #add device-input
    # GET-request
    response = requests.get(
        url="https://pa-api.telldus.com/json/devices/list",
        params={
            "supportedMethods": "3",
        },
        headers={
            "Authorization": 'OAuth oauth_consumer_key="{pubkey}", oauth_nonce="{nonce}", oauth_signature="{oauthSignature}", oauth_signature_method="PLAINTEXT", oauth_timestamp="{timestamp}", oauth_token="{token}", oauth_version="1.0"'.format(
                pubkey=pubkey, nonce=nonce, oauthSignature=oauthSignature, timestamp=timestamp, token=token),
        },
    )
    # Output/response from GET-request
    responseData = response.json()
    print(json.dumps(responseData, indent=4, sort_keys=True))