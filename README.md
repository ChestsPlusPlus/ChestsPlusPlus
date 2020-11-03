# ChestsPlusPlus [![Build Status](https://travis-ci.com/JamesPeters98/ChestsPlusPlus.svg?branch=master)](https://travis-ci.com/JamesPeters98/ChestsPlusPlus) 

<a href="https://www.buymeacoffee.com/jamespeters"><img src="https://img.buymeacoffee.com/button-api/?text=Buy me a coffee&emoji=&slug=jamespeters&button_colour=FF5F5F&font_colour=ffffff&font_family=Cookie&outline_colour=000000&coffee_colour=FFDD00"></a>

Minecraft Spigot mod that enhances chests and hoppers, with ChestLinks, Auto-Crafting and Hopper filters!

<p align="center">
  <img src="https://i.imgur.com/fFWiH5Y.png">
</p>

If your server is using this plugin let me know so I can feature it!

<p align="center">
  <img src="https://i.imgur.com/T1Cq6t8.png">
</p>

 
## Features:
  - Ability to link multiple chests together across the entire server to be accessed from anywhere!
  - Use Hoppers to filter items from chests using hoppers!
  - Setup Auto-Crafting Tables to automatically craft items from the inventory above!
  - Silk Touch can pick up ChestLinks and AutoCraft stations!
  - Displays the most common item in a chest on the front of it!
  - Build cool auto smelting/sorting systems!
  - Remotely open chests with a nice menu system!
  - Party system with a UI to share all your ChestLinks & AutoCrafters with other players!
  - Language support
  
## How to Create Linked Chests:
  - Add a Chest using **/chestlink add** <**group**>  or simply write the ChestLink format on a sign placed on a Chest!
  ![](https://i.gyazo.com/5ef24a3833e57bc0b3df230a90d67fb9.png)
  - Open a chest as you normally would! or use /chestlink open <group> or /chestlink menu to open the chest remotely!
  - Each chest that gets added to that group will share the same inventory with all other chests in that group!
  - *Note the **/chestlink** command can be replaced with **/cl** for convenience.*
  
## How to Create Auto-Crafting Stations
  - Add a Crafting Table using **/autocraft add** <**group**> or simply write the AutoCraft format on a sign placed on a Crafting Table!
  
  ![](https://i.imgur.com/RTeUOvX.png)
  - Open the Crafting Table and create your recipe (This requires at least one of each item and it won't use that item up!)
  - Once you have a valid recipe the table will notify you with a chime, at this point if the recipe has different variations such as wood type they will start to animate in the crafting grid.
  - To start crafting, a Chest (or any block with a valid inventory such as Furnaces, Barrels, Shulker Boxes, Hoppers etc) must be placed either on top or on any of the 3 sides not including the front.
  - Next, place a hopper underneath the Crafting Table and AutoCrafting will start automatically.
  - Alternatively, place a Chest or any other inventory block underneath the Crafting Table and apply a redstone signal to it.
  
  ### Torch AutoCraft Example:
  The following configurations work to AutoCraft torches with either a Hopper, or a chest underneath!
  ![](https://i.imgur.com/fpgNWLy.png)

## Hopper Filters
  - Hoppers can have filters applied to them so that they only accepted or reject certain items.
  - To add a filter to a Hopper you simply place an item frame on top or on any of its sides, and then place the item you would like to filter in it.
  - Currently there are four types of filters that can be enabled by rotating the item in the Item Frame.
    1. 0° Default - The Hopper will only accept this item. (Note multiple filters can be used on a hopper.)
    2. 45° Rejection Mode - The Hopper will prevent this item from being accepted but will accept other items.
    3. 90° Type-Acceptance - The Hopper will accept items that are of the same type, e.g Enchanted Books, Enchanted weapons, Potions etc.
        (NEW - This now includes woods, logs, fences, doors and lots of other groups of similar items)
    4. 135° Type-Rejection - The Hopper will reject all items that would normally be accepted in the filter above, so similar types are rejected. 
    
    ![](https://i.imgur.com/DU1rlxq.png)
  
## Example Sorting System:

![Example Sorting System](https://i.imgur.com/YNlMOiO.png)

## Sorting Multiple Items:

![Sorting Multiple Item](https://i.imgur.com/AiEZ6ic.png)

## Inventory Menu:

![Inventory Menu](https://i.imgur.com/StpFBYm.png)

## Party UI
### How to create a party
![Create a party](https://i.imgur.com/LLkq1ew.png)
![Anvil UI](https://i.imgur.com/6XrRKZ0.png)

### How invite a player to a party
![Invite menu](https://i.imgur.com/HtMrRkM.png)
![Party select](https://i.imgur.com/VG0GZNG.png)
![Player select](https://i.imgur.com/zcZKg6E.png)

### How to accept an invite

![chat message](https://i.imgur.com/6Rzc2gv.png)
![party invites](https://i.imgur.com/oW1NkG0.png)
![accept invite](https://i.imgur.com/EyN45J9.png)



## Commands:
  #### ChestLink Commands - **/chestlink** or **/cl** are accepted.
  
  - /chestlink add <Group> "Create/add a chest to a ChestLink group"
  - /chestlink remove <Group>  "Delete a ChestLink and drop its inventory at your feet!"  
  - /chestlink open <Group>  "Open the inventory of a ChestLink group"
  - /chestlink menu  "Open the ChestLink menu to display all groups!"
  - /chestlink help "List of commands and their uses!"
  - /chestlink list "Lists all ChestLinks that you own!"
  - /chestlink member [add/remove <group> <player>] or [list <group>] "Add, remove or list members of a group"
  - /chestlink member [add-to-all/remove-from-all] "Add/Remove a player to all of your ChestLinks"
  - /chestlink setpublic <group> <true/false> "Set a ChestLink to be accessible by anyone."
  - /chestlink rename <group> <new-name> "Rename a ChestLink."
  - /chestlink sort <group> <sort-method> "Set the sorting option for the given ChestLink."
  - /chestlink party "Open the party menu, to allow other players to access all your Chests and AutoCrafters."
  
    #### AutoCraft Commands - **/autocraft** or **/ac** are accepted.

  - /autocraft add <Group> "Create/add a Crafting Table to an AutoCraft group"
  - /autocraft remove <Group>  "Delete an AutoCraft group and drop all the Crafting Tables!"
  - /autocraft open <Group>  "Open the workbench of an AutoCraft group"
  - /autocraft help "List of commands and their uses!"
  - /autocraft list "Lists all AutoCraft groups that you own!"
  - /autocraft member [add/remove <group> <player>] or [list <group>] "Add, remove or list members of a group"
  - /autocraft member [add-to-all/remove-from-all] "Add/Remove a player to all of your AutoCraft groups"
  - /autocraft setpublic <group> <true/false> "Set an AutoCraft group to be accessible by anyone."
  - /autocraft rename <group> <new-name> ""Rename an AutoCraft group."
  - /autocraft party "Open the party menu, to allow other players to access all your Chests and AutoCrafters."
  
  #### ChestsPlusPlus Commands - **/chestsplusplus** or **/c++** are accepted.
  - /chestsplusplus party "Open the party menu, to allow other players to access all your Chests and AutoCrafters."
  - /chestsplusplus version
  
## Spotlights:

[![](http://img.youtube.com/vi/a1MvNNEe8NM/0.jpg)](http://www.youtube.com/watch?v=a1MvNNEe8NM "Spotlight")


## Tutorial:

[![](http://img.youtube.com/vi/Cxsvg539RQk/0.jpg)](http://www.youtube.com/watch?v=Cxsvg539RQk "Tutorial")

## How to install:
Simply drop the .jar file into the /plugins folder!
 

## Permissions:

```yaml
chestlink.add:
Gives permission to add ChestLinks!
default: true
```

```yaml
chestlink.open:
Gives permission to open ChestLinks!
default: true
```

```yaml
chestlink.menu:
Gives permission to open the ChestLink menu!
default: true
```

```yaml
chestlink.remove:
Gives permission to remove a ChestLink!
default: true
```

```yaml
chestlink.openall:
description: Gives permission to open all chests/autocraft stations, for admin use.
default: op
```

```yaml
chestlink.member:
description: Gives permission to add/remove a member to/from their chestlink.
default: true
```

```yaml
chestlink.sort:
description: Gives permission to sort ChestLinks.
default: true
```

```yaml
chestlink.autocraft.add:
Gives permission to add AutoCraft Stations!
default: true
```

```yaml
chestlink.autocraft.open:
Gives permission to open AutoCraft Stations!
default: true
```

```yaml
chestlink.autocraft.remove:
Gives permission to remove AutoCraft Stations!
default: true
```

```yaml
chestlink.party.create:
"Gives permission to create Chests++ parties."
default: true
```

```yaml
chestlink.party.invite:
"Gives permission to invite players to Chests++ parties."
default: true
```

```yaml
chestlink.party.accept_invite:
"Gives permission to accept Chests++ party invites."
default: true
```
 
# Donate!:
https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=4BCPAVJ7PBUUY&source=url
