package asd.itamio.autologinandregister;

import asd.itamio.ModInfoPrinter;
import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.login.LoginManager;
import asd.itamio.autologinandregister.register.RegisterManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

import java.io.File;

public class AutoLoginAndRegister implements ClientModInitializer {
    public static final String MODID = "autologinandregister";
    public static final String NAME = "Auto Login & Auto Register";
    public static final String VERSION = "2.1.0";
    public static final String AUTHOR = "Itamio";

    private static AutoLoginAndRegister instance;
    private ConfigManager configManager;
    private LoginManager loginManager;
    private RegisterManager registerManager;
    private File configDir;

    @Override
    public void onInitializeClient() {
        instance = this;

        this.configDir = new File(Minecraft.getInstance().gameDirectory, "config/autologininfo");
        if (!this.configDir.exists()) {
            this.configDir.mkdirs();
        }

        this.configManager = new ConfigManager(this.configDir);
        this.loginManager = new LoginManager(this.configManager);
        this.registerManager = new RegisterManager(this.configManager);
        this.configManager.loadConfig();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            this.loginManager.onJoin();
            this.registerManager.onJoin();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            this.loginManager.onTick();
            this.registerManager.onTick();
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            this.loginManager.onChatReceived(message);
            this.registerManager.onChatReceived(message);
            return true;
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            this.loginManager.registerCommand(dispatcher);
            this.registerManager.registerCommand(dispatcher);
            this.registerManager.registerCommandWatchers(dispatcher);
        });

        ModInfoPrinter.print(System.out::println, "Auto Login & Auto Register", "2.1.0");
    }

    public static AutoLoginAndRegister getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    public LoginManager getLoginManager() {
        return this.loginManager;
    }

    public RegisterManager getRegisterManager() {
        return this.registerManager;
    }

    public File getConfigDir() {
        return this.configDir;
    }
}
