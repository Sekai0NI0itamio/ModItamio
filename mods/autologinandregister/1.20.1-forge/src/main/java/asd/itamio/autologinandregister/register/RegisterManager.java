package asd.itamio.autologinandregister.register;

import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.util.CredentialManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class RegisterManager {
    private final ConfigManager configManager;
    private final CredentialManager credentialManager;
    private final Random random = new Random();
    private boolean isConnected = false;
    private boolean hasRegistered = false;
    private String currentServer = "";
    private int tickCount = 0;

    public RegisterManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.credentialManager = new CredentialManager();
    }

    public void registerCommandWatchers(CommandDispatcher<CommandSourceStack> dispatcher) {
        String loginPrefix = configManager.getCommandFormatLogin().split(" ")[0].replace("/", "");
        String registerPrefix = configManager.getCommandFormatRegister().split(" ")[0].replace("/", "");
        registerWatcher(dispatcher, loginPrefix, 1);
        registerWatcher(dispatcher, registerPrefix, 2);
    }

    private void registerWatcher(CommandDispatcher<CommandSourceStack> dispatcher, String prefix, int minArgs) {
        dispatcher.register(Commands.literal(prefix)
            .executes(ctx -> 1)
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .executes(ctx -> {
                    String argsStr = StringArgumentType.getString(ctx, "args");
                    handleWatcher(prefix, argsStr, minArgs);
                    return 1;
                })));
    }

    private void handleWatcher(String prefix, String argsStr, int minArgs) {
        String[] args = argsStr.split(" ");
        if (!this.isConnected || !this.configManager.isAutoSaveEnabled() || Minecraft.getInstance().player == null) {
            return;
        }
        if (args.length >= minArgs) {
            String password = args[0];
            String username = Minecraft.getInstance().player.getName().getString();
            String serverIp = Minecraft.getInstance().getCurrentServer().ip;
            this.credentialManager.savePassword(serverIp, username, password);
            String fullCommand = "/" + prefix + " " + argsStr;
            String label = (minArgs == 1) ? "login" : "register";
            if (this.configManager.isShowDebugMessages()) {
                CredentialManager.displayMessage(ChatFormatting.YELLOW + "Sending " + label + " command to server...");
            }
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.connection.sendChat(fullCommand);
            }
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() == Minecraft.getInstance().player) {
            if (Minecraft.getInstance().getCurrentServer() != null) {
                this.isConnected = true;
                this.hasRegistered = false;
                this.currentServer = Minecraft.getInstance().getCurrentServer().ip;
            } else {
                this.isConnected = false;
                this.hasRegistered = false;
                this.currentServer = "";
            }
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && this.isConnected && !this.hasRegistered && Minecraft.getInstance().player != null) {
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
        if (!this.isConnected || this.hasRegistered || !this.configManager.isAutoDetectRegister()) {
            return;
        }
        String message = event.getMessage().getString().toLowerCase();
        if (message.contains(this.configManager.getRegisterPattern().toLowerCase())) {
            this.attemptAutoRegister();
        }
    }

    public void attemptAutoRegister() {
        if (this.hasRegistered || !this.isConnected || Minecraft.getInstance().player == null) {
            return;
        }
        String username = Minecraft.getInstance().player.getName().getString();
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
            CredentialManager.displayMessage(ChatFormatting.YELLOW + "Sending register command...");
        }
        Minecraft.getInstance().player.connection.sendChat(command);
        this.credentialManager.savePassword(this.currentServer, username, password);
        if (this.configManager.isShowDebugMessages()) {
            CredentialManager.displayMessage(ChatFormatting.GREEN + "Auto-registered with a generated password (saved)");
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

    public void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(this.configManager.getRegisterCommand().replace(".", ""))
            .executes(ctx -> {
                attemptAutoRegister();
                return 1;
            }));
    }
}
