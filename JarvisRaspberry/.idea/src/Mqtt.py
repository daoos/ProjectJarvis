# Imports for MQTT
import time, datetime, threading, requests, json, hashlib, uuid, collections
import paho.mqtt.client as mqtt
import paho.mqtt.publish as publish

from Device import devices
from ShoppingList import ShoppingList, shoppingLists, deleteList, listAllLists, readList

# Set MQTT broker and topic
broker = "test.mosquitto.org"  # Broker
lamp_topic = "project-jarvis/floor-lamp"  # send messages to this topic
alarm_topic = "project-jarvis/alarm"
alarm_topic_create = "project-jarvis/alarm/create"
alarm_topic_control = "project-jarvis/alarm/control"
timer_topic = "project-jarvis/timer"
timer_topic_create = "project-jarvis/timer/create"
shopping_list_create = "project-jarvis/shopping-list/create"
shopping_list_delete = "project-jarvis/shopping-list/delete"
shopping_list_control = "project-jarvis/shopping-list/control"
shopping_list_read = "project-jarvis/shopping-list/read"
shopping_list_list_all = "project-jarvis/shopping-list/list-all"

running_topic = "project-jarvis/running" # UNNCESSARY?
feedback_topic = "project-jarvis/feedback"  # send feedback after activation
# TODO: Ändra sub-topic till alla lampor(går det att subba till en lista? iterera?) och pub-topic till status

# This is where to insert your generated API keys (http://api.telldus.com/keys)
pubkey = "FEHUVEW84RAFR5SP22RABURUPHAFRUNU"  # Public Key
privkey = "ZUXEVEGA9USTAZEWRETHAQUBUR69U6EF"  # Private Key
token = "a105fc203e00fd0d0958db3bace92026061b226ad"  # Token
secret = "b2368173e0fc4d83b8ffcc946fce97aa"  # Token Secret

localtime = time.localtime(time.time())
timestamp = str(time.mktime(localtime))
nonce = uuid.uuid4().hex
oauthSignature = (privkey + "%26" + secret)

############### MQTT section ##################
client = mqtt.Client()


# when connecting to mqtt do this;
def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Connection established. Code: " + str(rc))
    else:
        print("Connection failed. Code: " + str(rc))


def on_disconnect(client, userdata, rc):
    if rc != 0:
        print("Unexpected disonnection. Code: ", str(rc))
    else:
        print("Disconnected. Code: " + str(rc))


def on_publish(client, userdata, mid):
    print("Published: " + str(mid))


def on_subscribe(mosq, obj, mid, granted_qos):
    print("Subscribed: " + str(mid) + " " + str(granted_qos))


# TODO: Maybe change to a switch?
# reacts to a message in a subscribed topic
def on_message(client, userdata, msg):
    command = str(msg.payload.decode()).lower()
    print("Message received! " + msg.topic + " " + command)
    if (msg.topic == alarm_topic_create):
        commandList = command.split(",")
        alarmName = commandList[0]
        alarmLength = commandList[1]
        repeatable = commandList[2]
        print("ALARM COMMAND RECIEVED: " + alarmName + alarmLength + repeatable)
        setAlarm(alarmName, alarmLength, repeatable)
    elif (msg.topic == timer_topic_create):
        setTimer(command)
    elif (msg.topic == feedback_topic):
        print("Feedback sent: " + msg.topic + " " + command)
    elif ("device" in command):
        action(command, devices.get(msg.topic))
        client.publish(feedback_topic, str(command).title(), 1)  # publishes feedback after something has been done
    elif (msg.topic == shopping_list_create):
        #Creates a new instance of a shopping list with command as name
        ShoppingList(command)
    elif (msg.topic == shopping_list_delete):
        deleteList(command)
    elif (msg.topic == shopping_list_add):
        commandList = command.split(",")
        listName = commandList[0]
        item = commandList[1]
        list = shoppingLists.get(listName)
        publish(feedback_topic, list.addItem(item), 1)
    elif (msg.topic == shopping_list_remove):
        publish(shopping_list_read, removeItem(command), 1)
    elif (msg.topic == shopping_list_read):
        publish(shopping_list_read, readList(command), 1)
    elif (msg.topic == shopping_list_list_all):
        publish(feedback_topic, shoppingLists, 1)
    else:
        client.publish(feedback_topic, command.title() + " attempted, no applicable reaction", 1)

def on_log(client, userdata, level, buf):  # Message is in buf
    print("MQTT Log: " + str(buf))

### ALARMS ###

# LONG TERM:
# TODO: Separate alarm and timer into classes?
alarms = dict()
repeatableAlarms = dict()
# reminders = dict()
# timers = dict()

def setAlarm(alarmName, alarmLength, repeatable):
    timer = threading.Timer(float(alarmLength), alert)
    print("Alarm '" + alarmName + "' set for " + alarmLength + " seconds. Repeatable? " + repeatable)
    currentTime = datetime.datetime.now()
    print("Current time is " + currentTime.strftime("%H:%M:%S"))
    if (repeatable == "true"):
        repeatableAlarms.setdefault(alarmName, timer)
    else:
        alarms.setdefault(alarmName, timer)
    timer.start()


def cancelAlarm(alarmName):
    if alarms.get(alarmName) is not None:
        alarm = alarms.get(alarmName)
        print("Removing alarm: " + alarm)
        alarm.cancel()
        del alarms[alarm]
        print("Alarm " + alarmName + " has been cancelled")
    else:
        if repeatableAlarms.get(alarmName) is not None:
            alarm = repeatableAlarms.get(alarmName)
            print("Removing alarm: " + alarm)
            alarm.cancel()
            del repeatableAlarms[alarm]
            print("Alarm " + alarmName + " has been cancelled")
        else:
            print("ERROR: Alarm doesn't exist!")


def alert():
    alarm = (list(alarms.keys())[list(alarms.values()).index(threading.currentThread())])
    if alarms.get(alarm) is not None:
        print("ALERT: Alarm " + str(alarm) + " finished")
        # client.publish(alarm_topic_control, "play, " + str(alarm).title())
        #Delets the inactive alarms from the dict. Slow, maybe switch key and value?
        del alarms[alarm]
    else:
        alarm = repeatableAlarms.get(alarmName)
        print("ALERT: Alarm " + str(alarm) + " finished")
        #TODO: Set alarm for next day
    client.publish(alarm_topic_control, "play, " + str(alarm).title())


# def reminder(command):
#     # Break down the alarm into
#     reminderName = ""
#     commandTime = ""
#     setAlarm(commandTime)
#     reminders.setdefault(reminderName, alarms.get(commandTime))


# def setTimer(command):
#     commandTime = float(command)
#     timer = threading.Timer(commandTime, timerDone)
#     print("Alarm set for " + str(commandTime))
#     timers.setdefault(commandTime, timer)
#     timer.start()
#
# def cancelTimer(command):
#     timer = timers.get(float(command))
#     if timer is not None:
#         print("Alarm is " + str(timer))
#         timer.cancel()
#         del timers[float(command)]
#         print("Timer " + command + " has been cancelled")
#     else:
#         print("ERROR: Timer doesn't exist!")
#
# def timerDone():
#     print("Timer finished!")
#     client.publish(timer_topic, "play")


# def timedCommand(commandTopic, command, commandTime):
#     setTimer(commandTime, command, commandTime)
#     # add listener until above is ready?
#     client.publish(commandTopic, command.title())


#### OLD CONTROLLER ####

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


def status():  # add device-input
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


### MAIN ###
def main():
    # Connect functions for MQTT
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_publish = on_publish
    client.on_subscribe = on_subscribe
    client.on_message = on_message
    client.on_log = on_log

    # Connect to MQTT
    print("Attempting to connect to broker " + broker)
    client.connect(
        broker,
        1883)  # Broker address, port and keepalive (maximum period in seconds allowed between communications with the broker)
    client.subscribe(lamp_topic, 1)
    client.subscribe(feedback_topic, 1)
    client.subscribe(alarm_topic, 1)
    client.subscribe(alarm_topic_create, 1)
    # client.subscribe(alarm_topic_control, 1)
    client.subscribe(running_topic, 1)
    client.loop_start()

    # while True:
    # time.sleep(5)
    # data_to_send = datetime.datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
    # client.publish(running_topic, str("running " + data_to_send), 1)

    # setAlarm("5")
    # setAlarm("10")
    # setAlarm("15")
    # setAlarm("20")
    # # setTimer("5")
    # # setTimer("6")
    # # setTimer("7")
    # cancelAlarm("15")


    leksaksNamn = "leksaker"
    leksaksLista = ShoppingList(leksaksNamn)
    leksaksLista.addItem("Bil", 1)
    leksaksLista.addItem("Camera", 2)
    leksaksLista.addItem("Zebra", 3)
    readList(leksaksNamn)
    leksaksLista.removeItem("Zebra")
    readList(leksaksNamn)
    matLista = ShoppingList("Mat")
    listAllLists()
    readList(leksaksNamn)
    deleteList(leksaksNamn)
    listAllLists()

    while True:
         time.sleep(1)
         print(alarms)



main()  # runs the program
