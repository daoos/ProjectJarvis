# Imports for MQTT
import time
import datetime
import paho.mqtt.client as mqtt
import paho.mqtt.publish as publish

from Controller import control

# Set MQTT broker and topic
broker = "test.mosquitto.org"  # Broker
pub_topic = "project-jarvis/10124318"  # send messages to this topic
#TODO: Ändra sub-topic till alla lampor(går det att subba till en lista? iterera?) och pub-topic till status
sub_topic = pub_topic

############### MQTT section ##################

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


def on_message(client, userdata, msg):
    command = str(msg.payload.decode())
    print("Message received! " + msg.topic + " " + command)
    control(command)


def on_log(client, userdata, level, buf):  # Message is in buf
    print("MQTT Log: " + str(buf))


### MAIN ###
def main():
    # Connect functions for MQTT
    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.on_publish = on_publish
    client.on_subscribe = on_subscribe
    client.on_message = on_message
    client.on_log = on_log

    # Connect to MQTT
    print("Attempting to connect to broker " + broker)
    client.connect(
        broker)  # Broker address, port and keepalive (maximum period in seconds allowed between communications with the broker)
    client.subscribe(sub_topic, 1)
    client.loop_start()

    # # Loop that checks for updates
    # while True:
    #     client.subscribe(pub_topic)

    # Loop that publishes message
    while True:
        data_to_send = "turn on lamp"  # Here, call the correct function from the sensor section depending on sensor
        client.publish(pub_topic, str(data_to_send), 1)
        time.sleep(2.0)  # Set delay
        data_to_send = "turn off lamp"  # Here, call the correct function from the sensor section depending on sensor
        client.publish(pub_topic, str(data_to_send), 1)
        time.sleep(2.0)  # Set delay


main()  # runs the program
