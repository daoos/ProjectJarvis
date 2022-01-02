# from Mqtt import publish, feedback_topic

# Stores ALL the shoppinglists
# ListName: DictOfItems+Amounts
shoppingLists = {}

class ShoppingList:
    def __init__(self, name):
        self.name = name
        # The shopping list, Item: Amount
        self.list = {}
        shoppingLists.setdefault(self, list)

    def __repr__(self):
        return "<Shopping list:%s List contains:%s>" % (self.name, self.list)

    def __str__(self):
        return "From str method of Shopping list: %s, %s" % (self.name, self.list)

    def addItem(self, item, amount):
        if (int(amount) > 0):
            self.list.setdefault(item, amount)
            print("ADDING " + item + " to " + self.name)
            message = item + " " + str(amount) + " has been added to the shoppinglist " + self.name
        else:
            self.list.setdefault(item, 1)
            message = "I added one of " + str(item)
        return message

    def removeItem(self, item):
        print("REMOVING " + item + " from " + self.name)
        del self.list[item]
        return str(item + "has been removed")

    def getList(self):
        return self.list

def deleteList(name):
    print(name)
    print("^^^^^^^^^^")
    print(shoppingLists)
    print("New test: " + shoppingLists[name]['Bil'])
    shoppingList = shoppingLists.get(name)
    if name in shoppingLists:
        print("!!!" + name + "exists")

    if shoppingLists.get(name) is not None:
        del shoppingLists[name]
        print("DELETED! Shoppinglists now contains " + str(shoppingLists))
    else:
        print("43: The shopping list '" + str(name) + "' doesn't exist")


def listAllLists():
    print("ALL SHOPPING LISTS: " + str(shoppingLists))
    # publish(feedback_topic, shoppingLists, 1)


def readList(name):
    shoppingList = shoppingLists.get(name)
    if shoppingList is not None:
        print(str(name) + " exists!")
        listItems = ""
        for item in shoppingList:
            listItems + item + ", "
        message = "The list " + name + " contains: " + listItems + "if anything is missing, please add it."
    else:
        message ="That shopping list doesn't exist"
    return message
