package com.itamio.easyscreenshot;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScreenshotGalleryScreen extends Screen {
    private final Screen parent;
    private final List<String> screenshots;
    private final Set<Integer> selectedIndices = new HashSet<>();
    private static final int COLUMNS = 5;
    private static final int THUMB_SIZE = 64;
    private static final int PADDING = 8;

    public ScreenshotGalleryScreen(Screen parent) {
        super(Component.translatable("screen.easyscreenshot.gallery"));
        this.parent = parent;
        ScreenshotEventListener.refreshRecentList();
        this.screenshots = ScreenshotEventListener.getRecentScreenshots();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        addRenderableWidget(Button.builder(
                Component.translatable("screen.easyscreenshot.gallery.batch_delete"), btn -> {
                    if (!selectedIndices.isEmpty()) {
                        List<String> toDelete = new ArrayList<>();
                        for (int idx : selectedIndices) {
                            if (idx < screenshots.size()) {
                                toDelete.add(screenshots.get(idx));
                            }
                        }
                        for (String path : toDelete) {
                            File f = new File(path);
                            if (f.exists()) f.delete();
                            screenshots.remove(path);
                        }
                        selectedIndices.clear();
                        ScreenshotEventListener.refreshRecentList();
                    }
                }).pos(centerX - 110, this.height - 30).width(100).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.easyscreenshot.gallery.open_folder"), btn -> {
                    ScreenshotEventListener.openFolder(new File(
                            System.getProperty("user.home"), "Downloads"));
                }).pos(centerX + 10, this.height - 30).width(100).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.easyscreenshot.gallery.close"), btn -> {
                    if (minecraft != null) {
                        minecraft.setScreen(parent);
                    }
                }).pos(centerX - 50, this.height - 60).width(100).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        this.extractBackground(graphics, mouseX, mouseY, delta);
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.centeredText(this.font, title, this.width / 2, 10, 0xFFFFFF);

        int startX = (this.width - (COLUMNS * (THUMB_SIZE + PADDING))) / 2;
        int startY = 30;

        for (int i = 0; i < screenshots.size(); i++) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;

            int x = startX + col * (THUMB_SIZE + PADDING);
            int y = startY + row * (THUMB_SIZE + PADDING + 12);

            if (y > this.height - 80) break;

            String path = screenshots.get(i);
            File file = new File(path);
            String name = file.getName();

            boolean selected = selectedIndices.contains(i);
            int borderColor = selected ? 0xFF55FF55 : 0xFF555555;

            graphics.fill(x - 1, y - 1, x + THUMB_SIZE + 1, y + THUMB_SIZE + 1, borderColor);

            int bgColor = selected ? 0x33555555 : 0x33000000;
            graphics.fill(x, y, x + THUMB_SIZE, y + THUMB_SIZE, bgColor);

            String shortName = name.length() > 12 ? name.substring(0, 11) + "." : name;
            graphics.text(this.font, shortName, x, y + THUMB_SIZE + 2, 0xCCCCCC);

            if (mouseX >= x && mouseX <= x + THUMB_SIZE && mouseY >= y && mouseY <= y + THUMB_SIZE) {
                graphics.fill(x, y, x + THUMB_SIZE, y + THUMB_SIZE, 0x44FFFFFF);
                graphics.text(this.font, name, x + 2, y - 10, 0xFFFF55);
            }
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
