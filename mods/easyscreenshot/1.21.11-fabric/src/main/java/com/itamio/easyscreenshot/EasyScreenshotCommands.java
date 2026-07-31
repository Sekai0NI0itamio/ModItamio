package com.itamio.easyscreenshot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class EasyScreenshotCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("easyscreenshot")
                .then(Commands.literal("copy")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(EasyScreenshotCommands::executeCopy)))
                .then(Commands.literal("open")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(EasyScreenshotCommands::executeOpen)))
                .then(Commands.literal("openfolder")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(EasyScreenshotCommands::executeOpenFolder)))
                .then(Commands.literal("rename")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(EasyScreenshotCommands::executeRename)))
                .then(Commands.literal("delete")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(EasyScreenshotCommands::executeDelete)))
                .then(Commands.literal("gallery")
                        .executes(EasyScreenshotCommands::executeGallery))
        );
    }

    private static int executeCopy(CommandContext<CommandSourceStack> ctx) {
        String path = StringArgumentType.getString(ctx, "path");
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            ctx.getSource().sendFailure(Component.literal("File not found: " + path));
            return 0;
        }
        ClipboardHelper.copyImageToClipboard(file);
        ctx.getSource().sendSuccess(() -> Component.literal("Copied to clipboard: " + file.getName()), false);
        return 1;
    }

    private static int executeOpen(CommandContext<CommandSourceStack> ctx) {
        String path = StringArgumentType.getString(ctx, "path");
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            ctx.getSource().sendFailure(Component.literal("File not found: " + path));
            return 0;
        }
        ScreenshotEventListener.openFile(file);
        ctx.getSource().sendSuccess(() -> Component.literal("Opening: " + file.getName()), false);
        return 1;
    }

    private static int executeOpenFolder(CommandContext<CommandSourceStack> ctx) {
        String path = StringArgumentType.getString(ctx, "path");
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            ctx.getSource().sendFailure(Component.literal("File not found: " + path));
            return 0;
        }
        ScreenshotEventListener.openFolder(file.getParentFile());
        ctx.getSource().sendSuccess(() -> Component.literal("Opened folder for: " + file.getName()), false);
        return 1;
    }

    private static int executeRename(CommandContext<CommandSourceStack> ctx) {
        String path = StringArgumentType.getString(ctx, "path");
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            ctx.getSource().sendFailure(Component.literal("File not found: " + path));
            return 0;
        }

        try {
            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
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
                ctx.getSource().sendFailure(Component.literal("A file with name '" + suggestName + "' already exists. Try again."));
                return 0;
            }

            Files.move(file.toPath(), newFile.toPath());
            ScreenshotEventListener.refreshRecentList();
            ctx.getSource().sendSuccess(() -> Component.literal("Renamed to: " + newFile.getName()), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Failed to rename: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeDelete(CommandContext<CommandSourceStack> ctx) {
        String path = StringArgumentType.getString(ctx, "path");
        File file = new File(path);
        if (!file.exists()) {
            ctx.getSource().sendFailure(Component.literal("File already deleted or not found: " + path));
            return 0;
        }
        if (!file.isFile()) {
            ctx.getSource().sendFailure(Component.literal("Not a file: " + path));
            return 0;
        }
        String name = file.getName();
        boolean deleted = file.delete();
        if (deleted) {
            ScreenshotEventListener.refreshRecentList();
            ctx.getSource().sendSuccess(() -> Component.literal("Deleted: " + name), false);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.literal("Failed to delete: " + name));
            return 0;
        }
    }

    private static int executeGallery(CommandContext<CommandSourceStack> ctx) {
        try {
            net.minecraft.server.level.ServerPlayer player = ctx.getSource().getPlayerOrException();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "Press G to open the Screenshot Gallery, or use the keybind directly."), false);
        } catch (Exception ignored) {}
        return 1;
    }
}
