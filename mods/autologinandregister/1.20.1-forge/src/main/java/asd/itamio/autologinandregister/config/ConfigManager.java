package asd.itamio.autologinandregister.config;

import asd.itamio.autologinandregister.util.CredentialManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;
import net.minecraft.ChatFormatting;

public class ConfigManager {
    private static final String CONFIG_FILE = "config.txt";
    private boolean useDelay = true;
    private int delaySeconds = 1;
    private boolean autoDetectLoginPrompt = true;
    private String loginPattern = "login";
    private boolean disableOnceLoggedIn = true;
    private boolean loginCommandEnabled = true;
    private String loginCommand = ".autologin";
    private boolean autoRegisterEnabled = true;
    private int passwordLength = 6;
    private String contentPallet = "abcdefghijklmnopqrstuvwxyz0123456789";
    private boolean autoDetectRegister = true;
    private String registerPattern = "register";
    private boolean disableOnceRegistered = true;
    private boolean registerCommandEnabled = true;
    private String registerCommand = ".autoregister";
    private boolean autoSaveEnabled = true;
    private String commandFormatRegister = "/register $p $p";
    private String commandFormatLogin = "/login $p";
    private boolean showDebugMessages = false;
    private File configDir;
    private File configFile;

    public ConfigManager(File configDir) {
        this.configDir = configDir;
        this.configFile = new File(configDir, CONFIG_FILE);
    }

    public void loadConfig() {
        if (!this.configFile.exists()) {
            this.saveConfig();
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(this.configFile)) {
            props.load(fis);
            this.useDelay = Boolean.parseBoolean(props.getProperty("usedelay", "true"));
            this.delaySeconds = Integer.parseInt(props.getProperty("delayseconds", "1"));
            this.autoDetectLoginPrompt = Boolean.parseBoolean(props.getProperty("autodetectloginprompt", "true"));
            this.loginPattern = props.getProperty("loginpattern", "login");
            this.loginPattern = this.removeQuotes(this.loginPattern);
            this.disableOnceLoggedIn = Boolean.parseBoolean(props.getProperty("disableonceloggedin", "true"));
            this.loginCommandEnabled = Boolean.parseBoolean(props.getProperty("logincommand", "true"));
            this.loginCommand = props.getProperty("command", ".autologin");
            this.autoRegisterEnabled = Boolean.parseBoolean(props.getProperty("autoregister", "true"));
            this.passwordLength = Integer.parseInt(props.getProperty("passwordlength", "6"));
            this.contentPallet = props.getProperty("contentpallet", "abcdefghijklmnopqrstuvwxyz0123456789");
            this.contentPallet = this.removeQuotes(this.contentPallet);
            this.autoDetectRegister = Boolean.parseBoolean(props.getProperty("autodetectregister", "true"));
            this.registerPattern = props.getProperty("registerpattern", "register");
            this.registerPattern = this.removeQuotes(this.registerPattern);
            this.disableOnceRegistered = Boolean.parseBoolean(props.getProperty("disableonceregistered", "true"));
            this.registerCommandEnabled = Boolean.parseBoolean(props.getProperty("registercommand", "true"));
            this.registerCommand = props.getProperty("command", ".autoregister");
            this.autoSaveEnabled = Boolean.parseBoolean(props.getProperty("autosave", "true"));
            this.commandFormatRegister = props.getProperty("commandformatregister", "/register $p $p");
            this.commandFormatLogin = props.getProperty("commandformatlogin", "/login $p");
            this.showDebugMessages = Boolean.parseBoolean(props.getProperty("showdebugmessages", "false"));
            this.commandFormatRegister = this.removeQuotes(this.commandFormatRegister);
            this.commandFormatLogin = this.removeQuotes(this.commandFormatLogin);
            if (!this.commandFormatRegister.startsWith("/")) {
                this.commandFormatRegister = "/" + this.commandFormatRegister;
            }
            if (!this.commandFormatLogin.startsWith("/")) {
                this.commandFormatLogin = "/" + this.commandFormatLogin;
            }
        } catch (IOException | NumberFormatException e) {
            CredentialManager.displayMessage(ChatFormatting.RED + "Error loading config: " + e.getMessage());
            this.saveConfig();
        }
    }

    public void reloadConfig() {
        this.loadConfig();
        if (this.showDebugMessages) {
            CredentialManager.displayMessage(ChatFormatting.GREEN + "Configuration reloaded from disk");
        }
    }

    public void saveConfig() {
        try {
            if (!this.configDir.exists()) {
                this.configDir.mkdirs();
            }
            if (!this.configFile.exists()) {
                this.configFile.createNewFile();
            }
            if (!this.commandFormatRegister.startsWith("/")) {
                this.commandFormatRegister = "/" + this.commandFormatRegister;
            }
            if (!this.commandFormatLogin.startsWith("/")) {
                this.commandFormatLogin = "/" + this.commandFormatLogin;
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(this.configFile))) {
                writer.println("# ------------- Delay Based Auto Login -------------");
                writer.println("usedelay=" + this.useDelay);
                writer.println("delayseconds=" + this.delaySeconds);
                writer.println("# ------------- Auto Detect Login Message -------------");
                writer.println("autodetectloginprompt=" + this.autoDetectLoginPrompt);
                writer.println("loginpattern=\"" + this.loginPattern + "\"");
                writer.println("disableonceloggedin=" + this.disableOnceLoggedIn);
                writer.println("# ------------- Login Command -------------");
                writer.println("logincommand=" + this.loginCommandEnabled);
                writer.println("command=" + this.loginCommand);
                writer.println("# ------------- Auto Register -------------");
                writer.println("autoregister=" + this.autoRegisterEnabled);
                writer.println("passwordlength=" + this.passwordLength);
                writer.println("contentpallet=\"" + this.contentPallet + "\"");
                writer.println("# ------------- Auto Detect Register  -------------");
                writer.println("autodetectregister=" + this.autoDetectRegister);
                writer.println("registerpattern=\"" + this.registerPattern + "\"");
                writer.println("disableonceregistered=" + this.disableOnceRegistered);
                writer.println("# ------------- Register Command  -------------");
                writer.println("registercommand=" + this.registerCommandEnabled);
                writer.println("command=" + this.registerCommand);
                writer.println("# ------------- Auto Save Credentials -------------");
                writer.println("autosave=" + this.autoSaveEnabled);
                writer.println("commandformatregister=\"" + this.commandFormatRegister + "\"");
                writer.println("commandformatlogin=\"" + this.commandFormatLogin + "\"");
                writer.println("# ------------- Debug Settings -------------");
                writer.println("showdebugmessages=" + this.showDebugMessages);
            }
        } catch (IOException e) {
            CredentialManager.displayMessage(ChatFormatting.RED + "Error saving config: " + e.getMessage());
        }
    }

    private String removeQuotes(String input) {
        if (input == null) {
            return "";
        }
        if (input.startsWith("\"") && input.endsWith("\"")) {
            return input.substring(1, input.length() - 1);
        }
        return input;
    }

    public boolean isUseDelay() { this.loadConfig(); return this.useDelay; }
    public int getDelaySeconds() { this.loadConfig(); return this.delaySeconds; }
    public boolean isAutoDetectLoginPrompt() { this.loadConfig(); return this.autoDetectLoginPrompt; }
    public String getLoginPattern() { this.loadConfig(); return this.loginPattern; }
    public boolean isDisableOnceLoggedIn() { this.loadConfig(); return this.disableOnceLoggedIn; }
    public boolean isLoginCommandEnabled() { this.loadConfig(); return this.loginCommandEnabled; }
    public String getLoginCommand() { this.loadConfig(); return this.loginCommand; }
    public boolean isAutoRegisterEnabled() { this.loadConfig(); return this.autoRegisterEnabled; }
    public int getPasswordLength() { this.loadConfig(); return this.passwordLength; }
    public String getContentPallet() { this.loadConfig(); return this.contentPallet; }
    public boolean isAutoDetectRegister() { this.loadConfig(); return this.autoDetectRegister; }
    public String getRegisterPattern() { this.loadConfig(); return this.registerPattern; }
    public boolean isDisableOnceRegistered() { this.loadConfig(); return this.disableOnceRegistered; }
    public boolean isRegisterCommandEnabled() { this.loadConfig(); return this.registerCommandEnabled; }
    public String getRegisterCommand() { this.loadConfig(); return this.registerCommand; }
    public boolean isAutoSaveEnabled() { this.loadConfig(); return this.autoSaveEnabled; }
    public String getCommandFormatRegister() { this.loadConfig(); return this.commandFormatRegister; }
    public String getCommandFormatLogin() { this.loadConfig(); return this.commandFormatLogin; }
    public boolean isShowDebugMessages() { this.loadConfig(); return this.showDebugMessages; }
}
