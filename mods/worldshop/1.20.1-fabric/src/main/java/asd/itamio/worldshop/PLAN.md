# World Shop Changes Plan (1.20.1-fabric)

## New OP Admin Features (All features require OP permission level 2)

### What the user wants:
1. **X button on each block item** — OPs can click X to remove an item from the shop
2. **X button on each category** — OPs can click X to remove a category from the shop  
3. **Add Category button on home page** — Opens popup to name category and set block icon (search with preview)
4. **Add Block button on category page** — Opens search popup to add blocks/items to category
5. **/shop player command** — Shows the shop without admin controls (what normal players see)
6. **Edit button on items** — Opens edit popup to change name, icon, buy price, sell price

### Changes needed:

1. **GuiShopCategories.java** — Add `playerMode` flag:
   - OP mode: Draw X button on each category, show "Add Category" button  
   - Player mode: Original view without admin controls
   - Pass mode flag to GuiShopItems

2. **GuiShopItems.java** — Add `playerMode` flag:
   - OP mode: Draw X button on each item, Edit button on hover, "Add Block" button
   - Player mode: Original view without admin controls

3. **ModCommands.java** — Add `/shop player` command:
   - Sends OPEN_PLAYER_SHOP packet to open shop in player mode

4. **ClientPacketHandler.java** — Handle OPEN_PLAYER_SHOP:
   - Open GuiShopCategories in player mode

5. **ShopPacket.java** — Already has all admin packet types (REMOVE_ITEM, REMOVE_CATEGORY, ADD_CATEGORY, ADD_ITEM, EDIT_ITEM, OPEN_PLAYER_SHOP)

6. GuiAddCategory.java, GuiAddItem.java, GuiEditItem.java — Already exist and work correctly

7. ServerPacketHandler.java — Already handles all admin operations with permission checks

### OP detection:
- Client-side: `Minecraft.getInstance().player.hasPermissions(2)`  
- Server-side: `player.hasPermissions(2)` (already used in ServerPacketHandler)
- `/shop player` forces playerMode=true even for OPs
