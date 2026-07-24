package asd.itamio.worldshop;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * GUI handler for opening the vanilla shop container.
 * Registered with NetworkRegistry in WorldShop.preInit.
 */
public class ShopGuiHandler implements IGuiHandler {

    public static final int GUI_VANILLA_SHOP = 1000;

    @Override
    public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_VANILLA_SHOP) {
            VanillaShopContainer shop = new VanillaShopContainer(player);
            return shop.getMenu();
        }
        return null;
    }

    @Override
    public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
        if (ID == GUI_VANILLA_SHOP) {
            VanillaShopContainer shop = new VanillaShopContainer(player);
            return new GuiVanillaShop(shop.getMenu());
        }
        return null;
    }
}
