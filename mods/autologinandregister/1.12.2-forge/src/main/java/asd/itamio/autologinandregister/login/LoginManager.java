package asd.itamio.autologinandregister.login;

import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.util.CredentialManager;
import java.util.Timer;
import java.util.TimerTask;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class LoginManager {
    private final ConfigManager configManager;
    private final CredentialManager credentialManager;
    private final LoginCommand loginCommand;
    private boolean isConnected = false;
    private boolean hasLoggedIn = false;
    private String currentServer = "";
    private int tickCount = 0;

    public LoginManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.credentialManager = new CredentialManager();
        this.loginCommand = new LoginCommand();
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() == Minecraft.getMinecraft().player) {
            if (Minecraft.getMinecraft().getCurrentServerData() != null) {
                this.isConnected = true;
                this.hasLoggedIn = false;
                this.currentServer = Minecraft.getMinecraft().getCurrentServerData().serverIP;
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
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && this.isConnected && !this.hasLoggedIn && Minecraft.getMinecraft().player != null) {
            ++this.tickCount;
            if (this.tickCount >= 100) {
                this.tickCount = 0;
                if (Minecraft.getMinecraft().getCurrentServerData() == null) {
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
        String message = event.getMessage().getUnformattedText().toLowerCase();
        if (message.contains(this.configManager.getLoginPattern().toLowerCase())) {
            this.attemptAutoLogin();
        }
    }

    public void attemptAutoLogin() {
        if (this.hasLoggedIn || !this.isConnected || Minecraft.getMinecraft().player == null) {
            return;
        }
        String username = Minecraft.getMinecraft().player.getName();
        String password = this.credentialManager.getPassword(this.currentServer, username);
        if (password != null && !password.isEmpty()) {
            String command;
            String commandFormat = this.configManager.getCommandFormatLogin();
            if (commandFormat.startsWith("\"") && commandFormat.endsWith("\"")) {
                commandFormat = commandFormat.substring(1, commandFormat.length() - 1);
            }
            if (!(command = commandFormat.replace("$p", password)).startsWith("/")) {
                command = "/" + command;
            }
            if (this.configManager.isShowDebugMessages()) {
                CredentialManager.displayMessage(TextFormatting.YELLOW + "Sending login command...");
            }
            Minecraft.getMinecraft().player.sendChatMessage(command);
            if (this.configManager.isShowDebugMessages()) {
                CredentialManager.displayMessage(TextFormatting.GREEN + "Auto-login attempted for " + username);
            }
            if (this.configManager.isDisableOnceLoggedIn()) {
                this.hasLoggedIn = true;
            }
        } else if (this.configManager.isShowDebugMessages()) {
            CredentialManager.displayMessage(TextFormatting.RED + "No saved password found for " + username + " on " + this.currentServer);
        }
    }

    public CommandBase getCommand() {
        return this.loginCommand;
    }

    private class LoginCommand extends CommandBase {
        private LoginCommand() {
        }

        @Override
        public String getName() {
            return LoginManager.this.configManager.getLoginCommand().replace(".", "");
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/" + this.getName();
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return true;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            LoginManager.this.attemptAutoLogin();
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }
}
