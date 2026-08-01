package asd.itamio.autologinandregister.login;

import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.util.CredentialManager;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Timer;
import java.util.TimerTask;

public class LoginManager {
    private final ConfigManager configManager;
    private final CredentialManager credentialManager;
    private boolean isConnected = false;
    private boolean hasLoggedIn = false;
    private String currentServer = "";
    private int tickCount = 0;

    public LoginManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.credentialManager = new CredentialManager();
    }

    public void onJoin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.getCurrentServer() != null) {
            this.isConnected = true;
            this.hasLoggedIn = false;
            this.currentServer = mc.getCurrentServer().ip;
            if (this.configManager.isUseDelay()) {
                long delayMs = (long) this.configManager.getDelaySeconds() * 1000L;
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        LoginManager.this.attemptAutoLogin();
                    }
                }, delayMs);
            } else {
                this.attemptAutoLogin();
            }
        } else {
            this.isConnected = false;
            this.hasLoggedIn = false;
            this.currentServer = "";
        }
    }

    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        if (this.isConnected && !this.hasLoggedIn && mc.player != null) {
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
        if (!this.isConnected || this.hasLoggedIn || !this.configManager.isAutoDetectLoginPrompt()) {
            return;
        }
        String text = message.getString().toLowerCase();
        if (text.contains(this.configManager.getLoginPattern().toLowerCase())) {
            this.attemptAutoLogin();
        }
    }

    public void attemptAutoLogin() {
        Minecraft mc = Minecraft.getInstance();
        if (this.hasLoggedIn || !this.isConnected || mc.player == null) {
            return;
        }
        String username = mc.player.getName().getString();
        String password = this.credentialManager.getPassword(this.currentServer, username);
        if (password != null && !password.isEmpty()) {
            String commandFormat = this.configManager.getCommandFormatLogin();
            if (commandFormat.startsWith("\"") && commandFormat.endsWith("\"")) {
                commandFormat = commandFormat.substring(1, commandFormat.length() - 1);
            }
            String command = commandFormat.replace("$p", password);
            if (!command.startsWith("/")) {
                command = "/" + command;
            }
            if (this.configManager.isShowDebugMessages()) {
                CredentialManager.displayMessage("Sending login command...");
            }
            mc.player.connection.sendChat(command);
            if (this.configManager.isShowDebugMessages()) {
                CredentialManager.displayMessage("Auto-login attempted for " + username);
            }
            if (this.configManager.isDisableOnceLoggedIn()) {
                this.hasLoggedIn = true;
            }
        } else if (this.configManager.isShowDebugMessages()) {
            CredentialManager.displayMessage("No saved password found for " + username + " on " + this.currentServer);
        }
    }

    public void registerCommand(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        String commandName = this.configManager.getLoginCommand().replace(".", "");
        dispatcher.register(ClientCommandManager.literal(commandName)
                .executes(ctx -> {
                    this.attemptAutoLogin();
                    return 1;
                }));
    }
}
