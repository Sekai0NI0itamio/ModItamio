package com.itamio.easyscreenshot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RenameScreen extends Screen {
    private final String filePath;
    private final Screen parent;
    private EditBox nameField;
    private String errorMessage = "";

    public RenameScreen(String filePath, Screen parent) {
        super(Component.translatable("screen.easyscreenshot.rename"));
        this.filePath = filePath;
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        File file = new File(filePath);
        String currentName = file.getName();
        String baseName = currentName;
        String extension = "";
        int dotIndex = currentName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = currentName.substring(0, dotIndex);
            extension = currentName.substring(dotIndex);
        }

        nameField = new EditBox(this.font, centerX - 100, centerY - 20, 200, 20,
                Component.literal(""));
        nameField.setValue(baseName);
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);
        setInitialFocus(nameField);

        final String fileExt = extension;
        addRenderableWidget(Button.builder(Component.translatable("screen.easyscreenshot.rename.confirm"), btn -> {
            String newName = nameField.getValue().trim() + fileExt;
            File parentDir = new File(filePath).getParentFile();
            File newFile = new File(parentDir, newName);

            if (newName.equals(fileExt)) {
                errorMessage = "Name cannot be empty";
                return;
            }
            if (newFile.exists()) {
                errorMessage = "A file with that name already exists";
                return;
            }

            try {
                Files.move(Path.of(filePath), newFile.toPath());
                ScreenshotEventListener.refreshRecentList();
                if (minecraft != null) {
                    minecraft.setScreen(parent);
                }
            } catch (IOException e) {
                errorMessage = "Failed to rename: " + e.getMessage();
            }
        }).pos(centerX - 105, centerY + 10).width(100).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.easyscreenshot.rename.cancel"), btn -> {
            if (minecraft != null) {
                minecraft.setScreen(parent);
            }
        }).pos(centerX + 5, centerY + 10).width(100).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        graphics.drawCenteredString(this.font, title, centerX, 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.literal("Current: " + new File(filePath).getName()),
                centerX, this.height / 2 - 45, 0xAAAAAA);

        if (!errorMessage.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.literal(errorMessage).withStyle(net.minecraft.ChatFormatting.RED),
                    centerX, this.height / 2 + 35, 0xFF5555);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }
}
