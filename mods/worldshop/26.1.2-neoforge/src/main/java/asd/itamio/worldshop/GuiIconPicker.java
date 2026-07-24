package asd.itamio.worldshop;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GuiIconPicker extends ScreenManager.PopupScreen {
    private final Consumer<ItemStack> onIconSelected;

    private EditBox searchField;
    private String searchText = "";
    private List<ItemStack> searchResults = new ArrayList<>();
    private int searchScroll = 0;
    private ItemStack currentIcon;

    private static final int COLS = 7;
    private static final int SLOT_SIZE = 20;
    private static final int BG_COLOR = 0xFF1A1A1A;
    private static final int GAP_BELOW = 10;

    public GuiIconPicker(Screen parent, ItemStack currentIcon, Consumer<ItemStack> onIconSelected) {
        super(parent, Component.literal("Select Icon"));
        this.currentIcon = currentIcon.copy();
        this.onIconSelected = onIconSelected;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;

        this.searchField = new EditBox(this.font, centerX - 120, 35, 240, 16, Component.literal("Search icon..."));
        this.searchField.setMaxLength(40);
        this.searchField.setFocused(true);
        this.searchField.setResponder(this::onSearchChanged);
        this.addRenderableWidget(this.searchField);

        this.addRenderableWidget(Button.builder(Component.literal("\u00a7cBack to Edit"), btn -> {
            closeToParent();
        }).bounds(centerX - 50, this.height - 25, 100, 20).build());
    }

    private void onSearchChanged(String text) {
        this.searchText = text.toLowerCase().trim();
        this.searchScroll = 0;
        searchResults.clear();
        if (searchText.isEmpty()) return;

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) continue;
            String name = stack.getHoverName().getString().toLowerCase();
            String id = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase();
            if (name.contains(searchText) || id.contains(searchText)) {
                searchResults.add(stack);
                if (searchResults.size() >= 200) break;
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(0, 0, this.width, this.height, BG_COLOR);

        int centerX = this.width / 2;

        guiGraphics.centeredText(this.font, "\u00a76\u00a7lSelect Icon", centerX, 8, 0xFFFFFF);
        guiGraphics.centeredText(this.font, "\u00a77Search for a block/item to use as icon:", centerX, 22, 0xAAAAAA);

        guiGraphics.centeredText(this.font, "\u00a77Current icon:", centerX, 68, 0xAAAAAA);
        drawSlot(guiGraphics, centerX - 12, 78, 24, 24);
        guiGraphics.item(currentIcon, centerX - 10, 80);

        int startY = 115;
        int availableWidth = COLS * (SLOT_SIZE + 2);
        int guiLeft = (this.width - availableWidth) / 2;

        for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            int index = i + searchScroll * COLS;
            ItemStack stack = searchResults.get(index);
            drawSlot(guiGraphics, x, y, SLOT_SIZE, SLOT_SIZE);
            guiGraphics.item(stack, x + 2, y + 2);
        }

        for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                int index = i + searchScroll * COLS;
                guiGraphics.setTooltipForNextFrame(this.font, searchResults.get(index), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean inside) {
        double mouseX = event.x();
        double mouseY = event.y();
        int mouseButton = event.button();
        if (this.searchField != null) this.searchField.onClick(event, inside);

        for (var widget : this.children()) {
            if (widget instanceof Button btn && btn.mouseClicked(event, inside)) {
                return true;
            }
        }

        int startY = 115;
        int availableWidth = COLS * (SLOT_SIZE + 2);
        int guiLeft = (this.width - availableWidth) / 2;

        for (int i = 0; i < COLS * 4 && i + searchScroll * COLS < searchResults.size(); i++) {
            int col = i % COLS;
            int row = i / COLS;
            int x = guiLeft + col * (SLOT_SIZE + 2);
            int y = startY + row * (SLOT_SIZE + 2);
            if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                int index = i + searchScroll * COLS;
                ItemStack chosen = searchResults.get(index).copy();
                if (onIconSelected != null) {
                    onIconSelected.accept(chosen);
                }
                closeToParent();
                return true;
            }
        }

        return super.mouseClicked(event, inside);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, (int) Math.ceil((double) searchResults.size() / (double) COLS) - 4);
        if (scrollY > 0) searchScroll = Math.max(0, searchScroll - 1);
        else if (scrollY < 0) searchScroll = Math.min(maxScroll, searchScroll + 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == 256) {
            closeToParent();
            return true;
        }
        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.searchField != null && this.searchField.isFocused()) {
            return this.searchField.charTyped(event);
        }
        return super.charTyped(event);
    }

    private void drawSlot(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h) {
        guiGraphics.fill(x, y, x + w, y + h, -1438366652);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, -1439485133);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
