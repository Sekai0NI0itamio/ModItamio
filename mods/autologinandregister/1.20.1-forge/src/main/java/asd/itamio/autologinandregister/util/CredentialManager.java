package asd.itamio.autologinandregister.util;

import asd.itamio.autologinandregister.AutoLoginAndRegister;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class CredentialManager {
    private static final String CREDENTIALS_FILE = "playerstoredinfo.txt";
    private File credentialsFile = new File(AutoLoginAndRegister.getInstance().getConfigDir(), "playerstoredinfo.txt");

    public CredentialManager() {
        if (!this.credentialsFile.exists()) {
            try {
                this.credentialsFile.createNewFile();
            } catch (IOException e) {
                CredentialManager.displayMessage(ChatFormatting.RED + "Failed to create credentials file: " + e.getMessage());
            }
        }
    }

    private Map<String, String> loadCredentials() {
        HashMap<String, String> credentials = new HashMap<>();
        if (!this.credentialsFile.exists()) {
            try {
                this.credentialsFile.createNewFile();
            } catch (IOException e) {
                CredentialManager.displayMessage(ChatFormatting.RED + "Failed to create credentials file: " + e.getMessage());
                return credentials;
            }
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(this.credentialsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] newParts = line.split("\\|", 2);
                if (newParts.length == 2) {
                    String key = newParts[0].trim();
                    String password = newParts[1].trim();
                    credentials.put(key, password);
                    continue;
                }
                String[] oldParts = line.split(":", 3);
                if (oldParts.length != 3) continue;
                String serverIp = oldParts[0].trim();
                String username = oldParts[1].trim();
                String password = oldParts[2].trim();
                String key = serverIp + ":" + username;
                credentials.put(key, password);
            }
        } catch (IOException e) {
            CredentialManager.displayMessage(ChatFormatting.RED + "Failed to load credentials: " + e.getMessage());
        }
        return credentials;
    }

    public void savePassword(String serverIp, String username, String password) {
        Map<String, String> credentials = this.loadCredentials();
        String key = serverIp + ":" + username;
        boolean exists = credentials.containsKey(key);
        if (exists) {
            if (AutoLoginAndRegister.getInstance().getConfigManager().isShowDebugMessages()) {
                CredentialManager.displayMessage(ChatFormatting.YELLOW + "Password for " + username + " already exists, not updating");
            }
        } else {
            credentials.put(key, password);
            this.saveCredentials(credentials);
            if (AutoLoginAndRegister.getInstance().getConfigManager().isShowDebugMessages()) {
                CredentialManager.displayMessage(ChatFormatting.GREEN + "Saved new password for " + username + " on " + serverIp);
            }
        }
    }

    private void saveCredentials(Map<String, String> credentials) {
        try {
            if (this.credentialsFile.exists()) {
                File backup = new File(this.credentialsFile.getAbsolutePath() + ".backup");
                this.credentialsFile.renameTo(backup);
            }
            try (PrintWriter writer = new PrintWriter(new FileWriter(this.credentialsFile))) {
                for (Map.Entry<String, String> entry : credentials.entrySet()) {
                    writer.println(entry.getKey() + "|" + entry.getValue());
                }
            }
        } catch (IOException e) {
            CredentialManager.displayMessage(ChatFormatting.RED + "Failed to save credentials: " + e.getMessage());
        }
    }

    public String getPassword(String serverIp, String username) {
        Map<String, String> credentials = this.loadCredentials();
        return credentials.get(serverIp + ":" + username);
    }

    public static void displayMessage(String message) {
        if (Minecraft.getInstance().player != null && AutoLoginAndRegister.getInstance().getConfigManager().isShowDebugMessages()) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal("[AutoLogin] " + message), false);
        }
    }
}
