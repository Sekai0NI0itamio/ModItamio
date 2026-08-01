package asd.itamio.autologinandregister;

import asd.itamio.ModInfoPrinter;
import asd.itamio.autologinandregister.config.ConfigManager;
import asd.itamio.autologinandregister.login.LoginManager;
import asd.itamio.autologinandregister.register.RegisterManager;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = AutoLoginAndRegister.MODID, name = AutoLoginAndRegister.NAME, version = AutoLoginAndRegister.VERSION, clientSideOnly = true)
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

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        instance = this;
        ModInfoPrinter.print(LOGGER::info, NAME, VERSION);
        this.configDir = new File(Minecraft.getMinecraft().gameDir, "config/autologininfo");
        if (!this.configDir.exists()) {
            this.configDir.mkdirs();
        }
        this.configManager = new ConfigManager(this.configDir);
        this.loginManager = new LoginManager(this.configManager);
        this.registerManager = new RegisterManager(this.configManager);
        this.configManager.loadConfig();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this.loginManager);
        MinecraftForge.EVENT_BUS.register(this.registerManager);
        if (this.configManager.isLoginCommandEnabled()) {
            ClientCommandHandler.instance.registerCommand(this.loginManager.getCommand());
        }
        if (this.configManager.isRegisterCommandEnabled()) {
            ClientCommandHandler.instance.registerCommand(this.registerManager.getCommand());
        }
        this.registerManager.registerCommandWatchers();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        this.registerManager.onClientSendChat(event);
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
