package asd.itamio.worldshop;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Container;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Client-side GUI for the vanilla shop container.
 * Renders using the vanilla chest GUI texture for a familiar look.
 */
@SideOnly(Side.CLIENT)
public class GuiVanillaShop extends GuiContainer {

    private static final ResourceLocation CHEST_TEXTURE = new ResourceLocation("minecraft", "textures/gui/container/generic_54.png");
    private final int inventoryRows;

    public GuiVanillaShop(Container container) {
        super(container);
        this.inventoryRows = 6;
        this.xSize = 176;
        this.ySize = 114 + this.inventoryRows * 18;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(CHEST_TEXTURE);
        int i = (this.width - this.xSize) / 2;
        int j = (this.height - this.ySize) / 2;
        this.drawTexturedModalRect(i, j, 0, 0, this.xSize, this.inventoryRows * 18 + 17);
        this.drawTexturedModalRect(i, j + this.inventoryRows * 18 + 17, 0, 126, this.xSize, 96);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
