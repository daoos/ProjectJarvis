# from Mqtt import publish, feedback_topic

# Stores ALL the shoppinglists
# ListName: DictOfItems+Amounts
shoppingLists = {}


class ShoppingList:

    def __init__(self, name):
        self.name = name
        itemDict = {}
        self.itemDict = itemDict
        shoppingLists[name] = itemDict

    def __repr__(self):
        return "<Shopping list:%s contains:%s>" % (self.name, self.itemDict)

    def __str__(self):
        return "Shopping list %s contains: %s" % (self.name, self.itemDict)

    def addItem(self, item, amount):
        if (item != "nothing"):
            if (int(amount) > 0):
                self.itemDict.setdefault(item, amount)
                message = item + " " + str(amount) + " has been added to the shoppinglist " + self.name
            else:
                self.itemDict.setdefault(item, 1)
                message = "One " + str(item) + " has been added to the shoppinglist " + self.name
        else:
            message = item + "has been added to " + self.name
        return message

    def removeItem(self, item, amount):
        if (int(amount) > 0):
            currentAmount = self.itemDict.get(item)
            newAmount = currentAmount - amount
            self.itemDict.setdefault(item, newAmount)
            message = str(amount) + " " + item + " has been removed from the shoppinglist " + self.name
        else:
            del self.itemDict[item]
            self.itemDict.setdefault(item, 1)
            message = "All " + item + " has been removed from the shoppinglist " + self.name
        return message


def deleteList(name):
    if shoppingLists.get(name) is not None:
        del shoppingLists[name]
        message = "Removed shopping list: " + name
    else:
        message = "The shopping list '" + str(name) + "' doesn't exist"
    return message


def readList(name):
    shoppingList = shoppingLists.get(name)
    listItems = ""
    print("Shopping list is: " + str(shoppingList))
    for key in shoppingList:
        listItems += shoppingList.get(key) + ", " + key
    return "The list " + name + " contains: " + listItems


def listAllLists():
    print("ALL SHOPPING LISTS: " + str(shoppingLists))
    allLists = "Current shopping lists are: "
    if not shoppingLists:
        allLists += "\n" + "NONE"
    else:
        for key in shoppingLists.keys():
            allLists += "\n" + str(key).title()
    return allLists
