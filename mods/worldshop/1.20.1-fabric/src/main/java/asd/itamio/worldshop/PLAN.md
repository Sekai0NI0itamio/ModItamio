# World Shop Changes Plan (1.20.1-fabric) — Category Layout Reordering + Text Overlap Fix

## User's Change Request
1. Add an "Edit Layout" button on the home page (admin mode) that enters a drag/click-to-place category reordering mode showing grid empty slots
2. Save/Cancel buttons for confirmation
3. Fix text overlap when returning from category page to home page

## Changes Needed

### ShopPacket.java — Add REORDER_CATEGORIES type
- New type constant: REORDER_CATEGORIES = 12
- Serializes: an integer array of reordered category indices
- Factory + read/write methods

### ServerPacketHandler.java — Handle REORDER_CATEGORIES
- Verify OP permission (level 2)
- Accept int[] of new category indices order
- Reorder WorldShop.getCategories() list accordingly

### GuiShopCategories.java — Layout Edit Mode + Background Fix
- Add layoutEditMode toggle
- In layout mode: show fixed grid with empty slots drawn as outlined cells
- Click to pick up a category (highlight slot), click to place (swap/move to empty slot)
- Save button sends REORDER_CATEGORIES packet; Cancel reverts to original order
- Change background fill to FULLY opaque (0xFF1A1A1A) to prevent text overlap when returning from GuiShopItems
