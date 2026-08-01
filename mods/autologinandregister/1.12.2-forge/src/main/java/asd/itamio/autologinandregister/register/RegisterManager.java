package asd.itamio.autologinandregister.register;

import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.util.CredentialManager;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class RegisterManager {
    private final ConfigManager configManager;
    private final CredentialManager credentialManager;
    private final RegisterCommand registerCommand;
    private final LoginCommandWatcher loginCommandWatcher;
    private final RegisterCommandWatcher registerCommandWatcher;
    private final Random random = new Random();
    private boolean isConnected = false;
    private boolean hasRegistered = false;
    private String currentServer = "";
    private int tickCount = 0;

    public RegisterManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.credentialManager = new CredentialManager();
        this.registerCommand = new RegisterCommand();
        String loginPrefix = configManager.getCommandFormatLogin().split(" ")[0].replace("/", "");
        String registerPrefix = configManager.getCommandFormatRegister().split(" ")[0].replace("/", "");
        this.loginCommandWatcher = new LoginCommandWatcher(loginPrefix);
        this.registerCommandWatcher = new RegisterCommandWatcher(registerPrefix);
    }

    public void registerCommandWatchers() {
        ClientCommandHandler.instance.registerCommand((ICommand) this.loginCommandWatcher);
        ClientCommandHandler.instance.registerCommand((ICommand) this.registerCommandWatcher);
    }

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getEntity() == Minecraft.getMinecraft().player) {
            if (Minecraft.getMinecraft().getCurrentServerData() != null) {
                this.isConnected = true;
                this.hasRegistered = false;
                this.currentServer = Minecraft.getMinecraft().getCurrentServerData().serverIP;
            } else {
                this.isConnected = false;
                this.hasRegistered = false;
                this.currentServer = "";
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && this.isConnected && !this.hasRegistered && Minecraft.getMinecraft().player != null) {
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
        if (!this.isConnected || this.hasRegistered || !this.configManager.isAutoDetectRegister()) {
            return;
        }
        String message = event.getMessage().getUnformattedText().toLowerCase();
        if (message.contains(this.configManager.getRegisterPattern().toLowerCase())) {
            this.attemptAutoRegister();
        }
    }

    @SubscribeEvent
    public void onClientSendChat(PlayerEvent.PlayerLoggedInEvent event) {
    }

    public void attemptAutoRegister() {
        String command;
        if (this.hasRegistered || !this.isConnected || Minecraft.getMinecraft().player == null) {
            return;
        }
        String username = Minecraft.getMinecraft().player.getName();
        String password = this.generatePassword();
        String commandFormat = this.configManager.getCommandFormatRegister();
        if (commandFormat.startsWith("\"") && commandFormat.endsWith("\"")) {
            commandFormat = commandFormat.substring(1, commandFormat.length() - 1);
        }
        if (!(command = commandFormat.replace("$p", password)).startsWith("/")) {
            command = "/" + command;
        }
        if (this.configManager.isShowDebugMessages()) {
            CredentialManager.displayMessage(TextFormatting.YELLOW + "Sending register command...");
        }
        Minecraft.getMinecraft().player.sendChatMessage(command);
        this.credentialManager.savePassword(this.currentServer, username, password);
        if (this.configManager.isShowDebugMessages()) {
            CredentialManager.displayMessage(TextFormatting.GREEN + "Auto-registered with a generated password (saved)");
        }
        if (this.configManager.isDisableOnceRegistered()) {
            this.hasRegistered = true;
        }
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder();
        String contentPallet = this.configManager.getContentPallet();
        int length = this.configManager.getPasswordLength();
        for (int i = 0; i < length; ++i) {
            int index = this.random.nextInt(contentPallet.length());
            password.append(contentPallet.charAt(index));
        }
        return password.toString();
    }

    public CommandBase getCommand() {
        return this.registerCommand;
    }

    private class RegisterCommandWatcher extends CommandWatcher {
        public RegisterCommandWatcher(String commandPrefix) {
            super(commandPrefix);
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (!RegisterManager.this.isConnected || !RegisterManager.this.configManager.isAutoSaveEnabled() || Minecraft.getMinecraft().player == null) {
                return;
            }
            if (args.length >= 2) {
                String password = args[0];
                String username = Minecraft.getMinecraft().player.getName();
                String serverIp = Minecraft.getMinecraft().getCurrentServerData().serverIP;
                RegisterManager.this.credentialManager.savePassword(serverIp, username, password);
                String fullCommand = "/" + this.commandPrefix;
                for (String arg : args) {
                    fullCommand = fullCommand + " " + arg;
                }
                if (RegisterManager.this.configManager.isShowDebugMessages()) {
                    CredentialManager.displayMessage(TextFormatting.YELLOW + "Sending register command to server...");
                }
                if (Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendChatMessage(fullCommand);
                }
            }
        }
    }

    private class LoginCommandWatcher extends CommandWatcher {
        public LoginCommandWatcher(String commandPrefix) {
            super(commandPrefix);
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (!RegisterManager.this.isConnected || !RegisterManager.this.configManager.isAutoSaveEnabled() || Minecraft.getMinecraft().player == null) {
                return;
            }
            if (args.length >= 1) {
                String password = args[0];
                String username = Minecraft.getMinecraft().player.getName();
                String serverIp = Minecraft.getMinecraft().getCurrentServerData().serverIP;
                RegisterManager.this.credentialManager.savePassword(serverIp, username, password);
                String fullCommand = "/" + this.commandPrefix;
                for (String arg : args) {
                    fullCommand = fullCommand + " " + arg;
                }
                if (RegisterManager.this.configManager.isShowDebugMessages()) {
                    CredentialManager.displayMessage(TextFormatting.YELLOW + "Sending login command to server...");
                }
                if (Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendChatMessage(fullCommand);
                }
            }
        }
    }

    private abstract class CommandWatcher extends CommandBase {
        protected final String commandPrefix;

        public CommandWatcher(String commandPrefix) {
            this.commandPrefix = commandPrefix;
        }

        @Override
        public String getName() {
            return this.commandPrefix;
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/" + this.commandPrefix;
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return true;
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }

    private class RegisterCommand extends CommandBase {
        private RegisterCommand() {
        }

        @Override
        public String getName() {
            return RegisterManager.this.configManager.getRegisterCommand().replace(".", "");
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
            RegisterManager.this.attemptAutoRegister();
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }
    }
}
