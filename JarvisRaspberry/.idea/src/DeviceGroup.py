#Dictionary for storing devicegroups
deviceGroups = dict()

class DeviceGroup:

    def __init__(self, name, id, state):
        self.name = name
        topic = "project-jarvis/device/" + name.replace(" ", "-")
        self.id = id
        self.state = state
        deviceGroup = {"topic":topic, "id":id, "state":state}
        deviceGroups[name] = deviceGroup
        print(deviceGroup)

    def __repr__(self):
        return "<Devicegroup name:%s id:%s>" % (self.name, self.id)

    def __str__(self):
        return "From str method of DeviceGroup: name is %s, id is %s" % (self.name, self.id)