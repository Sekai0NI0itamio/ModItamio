package com.itamio.easyscreenshot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@EventBusSubscriber(modid = EasyScreenshot.MOD_ID)
public class EasyScreenshotCommands {

    public static void register() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("easyscreenshot")
                .then(Commands.literal("copy")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> executeCopy(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("open")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> executeOpen(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("openfolder")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> executeOpenFolder(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> executeRename(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> executeDelete(ctx.getSource(), StringArgumentType.getString(ctx, "path")))))
                .then(Commands.literal("gallery")
                        .executes(ctx -> executeGallery(ctx.getSource())))
        );
    }

    private static int executeCopy(CommandSourceStack source, String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            source.sendFailure(Component.literal("File not found: " + path));
            return 0;
        }
        ClipboardHelper.copyImageToClipboard(file);
        source.sendSuccess(() -> Component.literal("Copied to clipboard: " + file.getName()), false);
        return 1;
    }

    private static int executeOpen(CommandSourceStack source, String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            source.sendFailure(Component.literal("File not found: " + path));
            return 0;
        }
        ScreenshotEventListener.openFile(file);
        source.sendSuccess(() -> Component.literal("Opening: " + file.getName()), false);
        return 1;
    }

    private static int executeOpenFolder(CommandSourceStack source, String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            source.sendFailure(Component.literal("File not found: " + path));
            return 0;
        }
        ScreenshotEventListener.openFolder(file.getParentFile());
        source.sendSuccess(() -> Component.literal("Opened folder for: " + file.getName()), false);
        return 1;
    }

    private static int executeRename(CommandSourceStack source, String path) {
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            source.sendFailure(Component.literal("File not found: " + path));
            return 0;
        }

        try {
            String filename = file.getName();
            String baseName = filename;
            String extension = "";
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = filename.substring(0, dotIndex);
                extension = filename.substring(dotIndex);
            }

            String suggestName = baseName + "_renamed" + extension;
            File newFile = new File(file.getParent(), suggestName);

            if (newFile.exists()) {
                source.sendFailure(Component.literal("A file with name '" + suggestName + "' already exists. Try again."));
                return 0;
            }

            Files.move(file.toPath(), newFile.toPath());
            ScreenshotEventListener.refreshRecentList();
            source.sendSuccess(() -> Component.literal("Renamed to: " + newFile.getName()), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to rename: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDelete(CommandSourceStack source, String path) {
        File file = new File(path);
        if (!file.exists()) {
            source.sendFailure(Component.literal("File already deleted or not found: " + path));
            return 0;
        }
        if (!file.isFile()) {
            source.sendFailure(Component.literal("Not a file: " + path));
            return 0;
        }
        String name = file.getName();
        boolean deleted = file.delete();
        if (deleted) {
            ScreenshotEventListener.refreshRecentList();
            source.sendSuccess(() -> Component.literal("Deleted: " + name), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("Failed to delete: " + name));
            return 0;
        }
    }

    private static int executeGallery(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "Press G to open the Screenshot Gallery, or use the keybind directly."), false);
        return 1;
    }
}
