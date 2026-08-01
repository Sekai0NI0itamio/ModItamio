package asd.itamio.autologinandregister;

import asd.itamio.ModInfoPrinter;
import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.login.LoginManager;
import asd.itamio.autologinandregister.register.RegisterManager;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(AutoLoginAndRegister.MODID)
public class AutoLoginAndRegister {
    public static final String MODID = "autologinandregister";
    public static final String NAME = "Auto Login & Auto Register";
    public static final String VERSION = "2.1.0";
    private static final Logger LOGGER = LogManager.getLogger("AutoLoginAndRegister");
    private static AutoLoginAndRegister instance;
    private ConfigManager configManager;
    private LoginManager loginManager;
    private RegisterManager registerManager;
    private File configDir;

    public AutoLoginAndRegister() {
        instance = this;
        ModInfoPrinter.print(LOGGER::info, NAME, VERSION);
        this.configDir = new File(Minecraft.getInstance().gameDirectory, "config/autologininfo");
        if (!this.configDir.exists()) {
            this.configDir.mkdirs();
        }
        this.configManager = new ConfigManager(this.configDir);
        this.loginManager = new LoginManager(this.configManager);
        this.registerManager = new RegisterManager(this.configManager);
        this.configManager.loadConfig();
        MinecraftForge.EVENT_BUS.register(this.loginManager);
        MinecraftForge.EVENT_BUS.register(this.registerManager);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterClientCommands);
    }

    private void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        if (this.configManager.isLoginCommandEnabled()) {
            this.loginManager.registerCommand(event.getDispatcher());
        }
        if (this.configManager.isRegisterCommandEnabled()) {
            this.registerManager.registerCommand(event.getDispatcher());
        }
        this.registerManager.registerCommandWatchers(event.getDispatcher());
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
