#Dictionary for storing devices
devices = dict()

class Device:

    def __init__(self, name, id, state):
        self.name = name
        topic = "project-jarvis/device/" + name.replace(" ", "-")
        self.id = id
        self.state = state
        ## Unnecessary? Could remove and simply stor name:name and id:id in devices instead, or look at list and get
        ## the values from the class object
        device = {"topic":topic, "id":id, "state":state}
        devices[name] = device
        print(devices)

    def __repr__(self):
        return "<Device name:%s id:%s>" % (self.name, self.id)

    def __str__(self):
        return "From str method of Device: name is %s, id is %s" % (self.name, self.id)