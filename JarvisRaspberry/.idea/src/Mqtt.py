# Imports for MQTT
import time, datetime, threading, requests, json, hashlib, uuid, collections
import paho.mqtt.client as mqtt
import paho.mqtt.publish as publish

from Device import Device, devices
from DeviceGroup import DeviceGroup, deviceGroups
from ShoppingList import ShoppingList, shoppingLists, deleteList, readList, listAllLists

# Set MQTT broker and topic
broker = "test.mosquitto.org"  # Broker

devices_list_topic = "project-jarvis/devices/list" # list of all devices' topics, for the android to listen to

# SUBSCRIBE TOPICS
alarm_topic = "project-jarvis/alarm"
alarm_topic_create = "project-jarvis/alarm/create"
alarm_topic_control = "project-jarvis/alarm/control"
timer_topic_control = "project-jarvis/timer/control"
timer_topic_create = "project-jarvis/timer/create"
shopping_list_create = "project-jarvis/shopping-list/create"
shopping_list_delete = "project-jarvis/shopping-list/delete" # deletes the list
shopping_list_read = "project-jarvis/shopping-list/read"
shopping_list_read_all = "project-jarvis/shopping-list/read-all"
shopping_list_add = "project-jarvis/shopping-list/add"
shopping_list_remove = "project-jarvis/shopping-list/remove" # removes an item from the list
SUBSCRIBE_TOPICS = [
    alarm_topic, alarm_topic_create, alarm_topic_control,
    timer_topic_create, timer_topic_control, shopping_list_create, shopping_list_delete, shopping_list_add,
    shopping_list_remove, shopping_list_read, shopping_list_read_all
                    ] #All of the topics that the server subscribes to

running_topic = "project-jarvis/running" # To control that the server is running and working
feedback_topic = "project-jarvis/feedback"  # send feedback after activation

# TODO: Move to a token-file and add to gitIgnore. Maybe replaceable via a protected topic from the Android?
    ## Could also get these added from the android. Maybe use a ciphered topic by randomizing all the connected devices'
    ## topics + dateTime(dateTime for generating could be included in the device_list_topic, or simply decode using the
    ## same algorithm). Would allow us to use different tellsticks without hardcoding their keys here.
# This is where to insert your generated API keys (http://api.telldus.com/keys)

#Used for get-requests
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


# reacts to a message in a subscribed topic
def on_message(client, userdata, msg):
    command = str(msg.payload.decode()).lower()
    print("Message received! TOPIC: " + msg.topic + " MESSAGE: " + command)
    commandList = command.split(",")

    #   ALARMS
    if (msg.topic == alarm_topic_create): # UNFINISHED
        alarmName = commandList[0]
        alarmTarget = commandList[1]
        repeatable = commandList[2] #TODO: Changed from boolean to "every-day", "every-week" etc
        print("ALARM COMMAND RECIEVED: " + alarmName + alarmLength + repeatable)
        alarmLength = alarmTarget - time.time()
        setAlarm(alarmName, alarmLength, repeatable)
    #   TIMERS
    elif (msg.topic == timer_topic_create):
        setTimer(commandList[2])

    #     SHOPPING LISTS
    elif (msg.topic == shopping_list_create):
        ShoppingList(command)
        client.publish(feedback_topic, "Shopping list created: " + command, 1)
        #REMOVES A LIST
    elif (msg.topic == shopping_list_delete):
        client.publish(feedback_topic, deleteList(command), 1)
        #ADD ITEM TO LIST
    elif (msg.topic == shopping_list_add):
        name = commandList[0]
        item = commandList[1]
        amount = commandList[2]
        if name in shoppingLists:
            listName = shoppingLists.get(name)
            client.publish(feedback_topic, listName.addItem(item, amount), 1)
        else:
            client.publish(feedback_topic, "Failed to add " +  item + " to the shopping list " + name + ". List not found", 1)
        #REMOVE ITEM FROM LIST
    elif (msg.topic == shopping_list_remove):
        name = commandList[0]
        item = commandList[1]
        amount = commandList[2]
        if name in shoppingLists:
            listName = shoppingLists.get(name)
            client.publish(feedback_topic, listName.removeItem(item, amount), 1)
        else:
            client.publish(feedback_topic, "Failed to remove " + item + " from the shopping list " + name + ". List not found", 1)
        #READ LIST ALOUD
    elif (msg.topic == shopping_list_read):
        if (shoppingLists.get(command)) is not None:
            client.publish(feedback_topic, readList(command), 1)
        else:
            print("Not found, Lists are: " + str(shoppingLists))
            client.publish(feedback_topic, "That shopping list doesn't exist", 1)
        #LISTS ALL LISTS
    elif (msg.topic == shopping_list_read_all):
        client.publish(feedback_topic, listAllLists(), 1)

    #   DEVICES
    elif ("device/" or "devices/" in msg.topic):
        partName = msg.topic.rpartition("/")
        deviceName = partName[2]
        deviceName = deviceName.replace("-", " ")
        if deviceName in devices:
            if "setName" in command:
                newName = commandList[1]
                setTimedCommand(commandList[0], commandList[1], devices.get(deviceName).get("id"), newName)
            setTimedCommand(commandList[0], commandList[1], devices.get(deviceName).get("id"), deviceName)
        elif deviceName in deviceGroups:
            if "setName" in command:
                newName = commandList[1]
                setTimedCommand(commandList[0], commandList[1], deviceGroups.get(deviceName).get("id"), newName)
            setTimedCommand(commandList[0], commandList[1], deviceGroups.get(deviceName).get("id"), deviceName)
        else:
            splitCommand = command.rpartition("/")
            feedback = "ERROR: "+ splitCommand[len(splitCommand)-1] + ". Device not found"
            client.publish(feedback_topic, feedback, 1)  # publishes feedback after something has been done

    #   FEEDBACK
    elif (msg.topic == feedback_topic):
        print("Feedback sent: " + command)
    elif (msg.topic == running_topic):
        print("running")
    else:
        client.publish(feedback_topic, command.title() + " attempted, no applicable reaction", 1)


def on_log(client, userdata, level, buf):  # Message is in buf
    print("MQTT Log: " + str(buf))

### ALARMS ###

# LONG TERM:
# TODO: Separate alarm and timer into classes?
alarms = {}
repeatableAlarms = {}
commandTimers = {}
# reminders = dict()
# timers = dict()

def setAlarm(alarmName, alarmLength, special, unit):
    timer = threading.Timer(float(alarmLength), alert)
    print("Alarm '" + alarmName + "' set for " + alarmLength + unit + ". Special? " + special)
    currentTime = datetime.datetime.now()
    print("Current time is " + currentTime.strftime("%H:%M:%S"))

    if (alarmName != "noName"):
        if (special != "noSpecial"):
            alarms.setdefault(alarmName, timer)
        else:
            repeatableAlarms.setdefault(alarmName, timer)

    if (special == "every"):
        print(special)
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

def setTimer(time):
    commandTime = float(time)
    timer = threading.Timer(commandTime, timerDone)
    print("Timer set for " + str(time))
    timer.start()

# def cancelTimer(command):
#     timer = timers.get(float(command))
#     if timer is not None:
#         print("Alarm is " + str(timer))
#         timer.cancel()
#         del timers[float(command)]
#         print("Timer " + command + " has been cancelled")
#     else:
#         print("ERROR: Timer doesn't exist!")

def timerDone():
    print("Timer finished!")
    client.publish(timer_topic_control, "play")
    client.publish(feedback_topic, "Time's up!")

def setTimedCommand(command, time, id, name):
    commandTime = float(time)
    timer = threading.Timer(commandTime, fireTimedCommand, args=(command, id, name,))
    print("Device " + name + "("+id+")" + " will " + command + " in " + str(time))
    data = [name, id, command, time]
    commandTimers.setdefault(timer, data)
    timer.start()

def fireTimedCommand(command, id, name):
    timer = threading.currentThread()
    del commandTimers[timer]
    action(command, id, name)

def updateDevices():
    createExistingDevices()
    allDeviceTopics = ""
    for key in devices.keys():
        device = devices.get(key)
        topic = device.get("topic")
        client.subscribe(topic, 1)
        allDeviceTopics += (topic + ",")
    for key in deviceGroups.keys():
        client.subscribe(key, 1)
        allDeviceTopics += (key + ",")
    print("!!ALL DEVICE TOPICS UPDATED = " + allDeviceTopics)
    client.publish(devices_list_topic, allDeviceTopics, 1, True)

def createExistingDevices():
    # GET-request
    response = requests.get(
        url="https://pa-api.telldus.com/json/devices/list",
        params={
            "supportedMethods": "3",
        },
        headers={
            "Authorization": 'OAuth oauth_consumer_key="{pubkey}", oauth_nonce="{nonce}",'
                             'oauth_signature="{oauthSignature}", oauth_signature_method="PLAINTEXT",'
                             'oauth_timestamp="{timestamp}", oauth_token="{token}", oauth_version="1.0"'.format(
                pubkey=pubkey, nonce=nonce, oauthSignature=oauthSignature, timestamp=timestamp, token=token),
        },
    )
    # Output/response from GET-request
    data = response.json().get('device')
    for i in data:
        name = i.get("name")
        id = i.get("id")
        state = i.get("state")
        if i.get("type") == "device":
            Device(name, id, state)
        elif i.get("type") == "group":
            DeviceGroup(name, id, state)
        else:
            print("ERROR: Unsupported device type: " + i)

#### OLD CONTROLLER CLASS ####

def action(command, id, name):
    # GET-request
    response = requests.get(
        url="https://pa-api.telldus.com/json/" + command,
        params={
            "id": id,
            "name": name,
        },
        headers={
            "Authorization": 'OAuth oauth_consumer_key="{pubkey}", oauth_nonce="{nonce}",'
                             'oauth_signature="{oauthSignature}", oauth_signature_method="PLAINTEXT",'
                             'oauth_timestamp="{timestamp}", oauth_token="{token}", oauth_version="1.0"'.format(
                pubkey=pubkey, nonce=nonce, oauthSignature=oauthSignature, timestamp=timestamp, token=token),
        },
    )
    # Output/response from GET-request
    responseData = response.text
    print(json.dumps(responseData, indent=4, sort_keys=True))
    feedback = "Command " + command + " not possible for device " + name + "("+ id +")"
    if ("failure" and "error") not in responseData:
        if ("on" in command):
            feedback = "Turned on " + name
        elif ("off" in command):
            feedback = "Turned off " + name
        elif ("bell" in command):
            feedback = "Ringing the bell on " + name
        else:
            feedback = "Can't identify the command. Applied command " + command + " to " + name
    client.publish(feedback_topic, feedback)


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
    ## TODO: Change to a more secure topic, see https://test.mosquitto.org/
    client.connect(
        broker,
        1883)  # Broker address, port and keepalive (maximum period in seconds allowed between communications with the broker)

    for topic in SUBSCRIBE_TOPICS:
        client.subscribe(topic, 1)
    updateDevices()
    client.loop_start()

    # ## TESTING
    # ShoppingList("toys")
    # toyList = shoppingLists.get("toys")
    # print(toyList)
    # toyList.addItem("car", 1)
    # toyList.addItem("camera", 2)
    # toyList.addItem("zebra", 3)
    # toyList.removeItem("zebra", 2)
    # # shoppingLists.setdefault(toyList)
    # ShoppingList("food")
    # foodList = shoppingLists.get("food")
    # # shoppingLists.setdefault(foodList)
    # print(listAllLists())
    # readList(toyList)
    # # client.publish(feedback_topic, readList(toyList))
    # ## END TESTING

    while True:
        time.sleep(5)
        data_to_send = datetime.datetime.now().strftime("%m/%d/%Y, %H:%M:%S")
        print(data_to_send)
        # client.publish(running_topic, str("running " + data_to_send), 1)
        updateDevices()

main()  # runs the program
