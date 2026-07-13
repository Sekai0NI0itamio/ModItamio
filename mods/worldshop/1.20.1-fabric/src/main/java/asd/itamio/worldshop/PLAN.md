# World Shop Changes Plan (1.20.1-fabric) — Fix Overlaps + Reusable Screen System

## User's Change Request (3 issues)

### Issue 1: X and Edit buttons overlap with item renders
**Problem:** In GuiShopItems grid view (admin mode), the X (remove) button at top-right and E (edit) button at top-left of each slot overlap with the rendered item icon at (x+3, y+3).
**Fix:** Move X and E buttons BELOW the item slot. Increase cell height from 26 to 34 when admin mode is active, placing the item at the top and the X/E buttons in a small row below the slot.

### Issue 2: Screen overlaps when editing/creating
**Problem:** When opening GuiEditItem, GuiAddCategory, or GuiAddItem from the main shop screens, the previous screen's content appears behind the new screen's semi-transparent background.
**Fix:** 
- Create a reusable `ScreenManager` class that manages screen transitions:
  - Opens screens with full cleanup of the previous screen
  - Provides `openAsPopup(Screen parent, Screen popup)` method that stores the parent for proper back-navigation
- Make popup screen backgrounds fully opaque (0xFF2A2A2A) instead of semi-transparent

### Issue 3: Item icon not centered in purchase detail view
**Problem:** In the detail view (both GuiShopCategories and GuiShopItems), the item icon is rendered at translate(centerX - 8, itemCenterY - 8) with scale(2), which positions it at (centerX-8, itemCenterY-8) to (centerX+24, itemCenterY+24) instead of being centered in the 48x48 slot at (centerX-24, itemCenterY-24) to (centerX+24, itemCenterY+24).
**Fix:** Change translate to (centerX - 16, itemCenterY - 16) so the 32x32 scaled item is properly centered in the 48x48 slot.

## Files to Modify:
1. **NEW: ScreenManager.java** — Reusable screen transition system
2. **GuiShopItems.java** — Move X/E buttons below items, fix icon centering, use ScreenManager
3. **GuiShopCategories.java** — Fix icon centering in detail view
4. **GuiEditItem.java** — Use opaque background, use ScreenManager
5. **GuiAddCategory.java** — Use opaque background, use ScreenManager
6. **GuiAddItem.java** — Use opaque background, use ScreenManager
