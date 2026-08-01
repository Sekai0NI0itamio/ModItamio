package asd.itamio.autologinandregister.register;

import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.util.CredentialManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Random;

public class RegisterManager {
    private final ConfigManager configManager;
    private final CredentialManager credentialManager;
    private final Random random = new Random();
    private boolean isConnected = false;
    private boolean hasRegistered = false;
    private String currentServer = "";
    private int tickCount = 0;
    private final String loginPrefix;
    private final String registerPrefix;

    public RegisterManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.credentialManager = new CredentialManager();
        this.loginPrefix = configManager.getCommandFormatLogin().split(" ")[0].replace("/", "");
        this.registerPrefix = configManager.getCommandFormatRegister().split(" ")[0].replace("/", "");
    }

    public void registerCommandWatchers(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal(this.loginPrefix)
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            this.onLoginWatcher(StringArgumentType.getString(ctx, "args"));
                            return 1;
                        })));
        dispatcher.register(ClientCommands.literal(this.registerPrefix)
                .then(ClientCommands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            this.onRegisterWatcher(StringArgumentType.getString(ctx, "args"));
                            return 1;
                        })));
    }

    private void onLoginWatcher(String args) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.isConnected || !this.configManager.isAutoSaveEnabled() || mc.player == null) {
            return;
        }
        String[] parts = args.split(" ");
        if (parts.length >= 1) {
            String password = parts[0];
            String username = mc.player.getName().getString();
            String serverIp = (mc.getCurrentServer() != null) ? mc.getCurrentServer().ip : "";
            this.credentialManager.savePassword(serverIp, username, password);
            String fullCommand = "/" + this.loginPrefix;
            for (String arg : parts) {
                fullCommand = fullCommand + " " + arg;
            }
            if (this.configManager.isShowDebugMessages()) {
                CredentialManager.displayMessage("Sending login command to server...");
            }
            if (mc.player != null) {
                mc.player.connection.sendChat(fullCommand);
            }
        }
    }

    private void onRegisterWatcher(String args) {
        Minecraft mc = Minecraft.getInstance();
        if (!this.isConnected || !this.configManager.isAutoSaveEnabled() || mc.player == null) {
            return;
        }
        String[] parts = args.split(" ");
        if (parts.length >= 2) {
            String password = parts[0];
            String username = mc.player.getName().getString();
            String serverIp = (mc.getCurrentServer() != null) ? mc.getCurrentServer().ip : "";
            this.credentialManager.savePassword(serverIp, username, password);
            String fullCommand = "/" + this.registerPrefix;
            for (String arg : parts) {
                fullCommand = fullCommand + " " + arg;
            }
            if (this.configManager.isShowDebugMessages()) {
                CredentialManager.displayMessage("Sending register command to server...");
            }
            if (mc.player != null) {
                mc.player.connection.sendChat(fullCommand);
            }
        }
    }

    public void onJoin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.getCurrentServer() != null) {
            this.isConnected = true;
            this.hasRegistered = false;
            this.currentServer = mc.getCurrentServer().ip;
        } else {
            this.isConnected = false;
            this.hasRegistered = false;
            this.currentServer = "";
        }
    }

    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (this.isConnected && !this.hasRegistered && mc.player != null) {
            this.tickCount++;
            if (this.tickCount >= 100) {
                this.tickCount = 0;
                if (mc.getCurrentServer() == null) {
                    this.isConnected = false;
                }
            }
        }
    }

    public void onChatReceived(Component message) {
        if (!this.isConnected || this.hasRegistered || !this.configManager.isAutoDetectRegister()) {
            return;
        }
        String text = message.getString().toLowerCase();
        if (text.contains(this.configManager.getRegisterPattern().toLowerCase())) {
            this.attemptAutoRegister();
        }
    }

    public void attemptAutoRegister() {
        Minecraft mc = Minecraft.getInstance();
        if (this.hasRegistered || !this.isConnected || mc.player == null) {
            return;
        }
        String username = mc.player.getName().getString();
        String password = this.generatePassword();
        String commandFormat = this.configManager.getCommandFormatRegister();
        if (commandFormat.startsWith("\"") && commandFormat.endsWith("\"")) {
            commandFormat = commandFormat.substring(1, commandFormat.length() - 1);
        }
        String command = commandFormat.replace("$p", password);
        if (!command.startsWith("/")) {
            command = "/" + command;
        }
        if (this.configManager.isShowDebugMessages()) {
            CredentialManager.displayMessage("Sending register command...");
        }
        mc.player.connection.sendChat(command);
        this.credentialManager.savePassword(this.currentServer, username, password);
        if (this.configManager.isShowDebugMessages()) {
            CredentialManager.displayMessage("Auto-registered with a generated password (saved)");
        }
        if (this.configManager.isDisableOnceRegistered()) {
            this.hasRegistered = true;
        }
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder();
        String contentPallet = this.configManager.getContentPallet();
        int length = this.configManager.getPasswordLength();
        for (int i = 0; i < length; i++) {
            int index = this.random.nextInt(contentPallet.length());
            password.append(contentPallet.charAt(index));
        }
        return password.toString();
    }

    public void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        String commandName = this.configManager.getRegisterCommand().replace(".", "");
        dispatcher.register(ClientCommands.literal(commandName)
                .executes(ctx -> {
                    this.attemptAutoRegister();
                    return 1;
                }));
    }
}
