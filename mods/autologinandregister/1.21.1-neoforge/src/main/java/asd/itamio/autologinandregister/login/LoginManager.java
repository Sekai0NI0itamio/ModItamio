package asd.itamio.autologinandregister.login;

import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.util.CredentialManager;
import com.mojang.brigadier.CommandDispatcher;
import java.util.Timer;
import java.util.TimerTask;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

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

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() == Minecraft.getInstance().player) {
            if (Minecraft.getInstance().getCurrentServer() != null) {
                this.isConnected = true;
                this.hasLoggedIn = false;
                this.currentServer = Minecraft.getInstance().getCurrentServer().ip;
                if (this.configManager.isUseDelay()) {
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            LoginManager.this.attemptAutoLogin();
                        }
                    }, (long) this.configManager.getDelaySeconds() * 1000L);
                } else {
                    this.attemptAutoLogin();
                }
            } else {
                this.isConnected = false;
                this.hasLoggedIn = false;
                this.currentServer = "";
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (this.isConnected && !this.hasLoggedIn && Minecraft.getInstance().player != null) {
            ++this.tickCount;
            if (this.tickCount >= 100) {
                this.tickCount = 0;
                if (Minecraft.getInstance().getCurrentServer() == null) {
                    this.isConnected = false;
                }
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!this.isConnected || this.hasLoggedIn || !this.configManager.isAutoDetectLoginPrompt()) {
            return;
        }
        String message = event.getMessage().getString().toLowerCase();
        if (message.contains(this.configManager.getLoginPattern().toLowerCase())) {
            this.attemptAutoLogin();
        }
    }

    public void attemptAutoLogin() {
        if (this.hasLoggedIn || !this.isConnected || Minecraft.getInstance().player == null) {
            return;
        }
        String username = Minecraft.getInstance().player.getName().getString();
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
                CredentialManager.displayMessage(ChatFormatting.YELLOW + "Sending login command...");
            }
            Minecraft.getInstance().player.connection.sendChat(command);
            if (this.configManager.isShowDebugMessages()) {
                CredentialManager.displayMessage(ChatFormatting.GREEN + "Auto-login attempted for " + username);
            }
            if (this.configManager.isDisableOnceLoggedIn()) {
                this.hasLoggedIn = true;
            }
        } else if (this.configManager.isShowDebugMessages()) {
            CredentialManager.displayMessage(ChatFormatting.RED + "No saved password found for " + username + " on " + this.currentServer);
        }
    }

    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(this.configManager.getLoginCommand().replace(".", ""))
            .executes(ctx -> {
                attemptAutoLogin();
                return 1;
            }));
    }
}
