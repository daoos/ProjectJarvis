#Dictionary (instead of javas hashmap) for storing devices
devices = dict()

#internal class for Devices
class Device:

    def __init__(self, name, id):
        self.name = name
        self.id = id
        devices[name] = id #Every time a device is created, it's added to the dict devices

    def __repr__(self):
        return "<Device name:%s id:%s>" % (self.name, self.id)

    def __str__(self):
        return "From str method of Device: name is %s, b is %s" % (self.name, self.id)

# floorLamp = Device("FloorLamp", "10124318")
# testLamp = Device("TestLampy", "202")