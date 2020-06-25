# ChestsPlusPlus

Minecraft Spigot mod that enhances chests and hoppers, with ChestLinks and Hopper filters!

<p align="center">
  <img src="https://i.imgur.com/zQgesHB.png">
</p>

If your server is using this plugin let me know so I can feature it!
 
## What it does:
  - Ability to link multiple chests together across the entire server to be accessed from anywhere!
  - Use Hoppers to filter items from chests using hoppers!
  - Build cool auto smelting/sorting systems!
  - Remotely open chests with a nice menu system!
  
## How to use:
  - Add a chest using /chestlink add <group> or simply write the ChestLink format on a sign placed on a chest!
  ![](https://i.gyazo.com/5ef24a3833e57bc0b3df230a90d67fb9.png)
  - Open a chest as you normally would! or use /chestlink open <group> or /chestlink menu to open the chest remotely!
  - Filter chests using Hoppers with Item Frames! Any hopper with an Item Frame on it with an item inside will only pull items of that type! (Note: You can add multiple item frames to a hopper to filter multiple items!)
  - Build giant smelting and sorting systems and share your creations!
  
## Example Sorting System:

![Example Sorting System](https://i.imgur.com/YNlMOiO.png)

## Sorting Multiple Items:

![Sorting Multiple Item](https://i.imgur.com/AiEZ6ic.png)

## Inventory Menu:

![Inventory Menu](https://i.imgur.com/StpFBYm.png)

## Commands:

  - /chestlink add <Group> "Create/add a chest to a ChestLink group"
  - /chestlink remove <Group>  "Delete a ChestLink and drop its inventory at your feet!"  
  - /chestlink open <Group>  "Open the inventory of a ChestLink group"
  - /chestlink menu  "Open the ChestLink menu to display all groups!"
  - /chestlink help "List of commands and their uses!"
  - /chestlink list "Lists all ChestLinks that you own!"
  - /chestlink member [add/remove <group> <player>] or [list <group>] "Add, remove or list members of a group"
  - /chestlink setpublic <group> <true/false> "Set a ChestLink to be accessible by anyone."
  - /chestlink rename <group> <new-name> "Rename a ChestLink."
  - /chestlink sort <group> <sort-method> "Set the sorting option for the given ChestLink."
  
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
description: Gives permission to open all chests, for admin use.
default: false
```

```yaml
chestlink.member:
description: Gives permission to add/remove a member to/from their chestlink.
default: true
```
 
# Donate!:
https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=4BCPAVJ7PBUUY&source=url
