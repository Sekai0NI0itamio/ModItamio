package asd.itamio.antichatlag;

import asd.itamio.ModInfoPrinter;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AntiChatLag implements ClientModInitializer {
    public static final String MOD_ID = "antichatlag";
    public static final String MOD_VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("AntiChatLag");

    @Override
    public void onInitializeClient() {
        ModInfoPrinter.print(LOGGER::info, "Anti Chat Lag", MOD_VERSION);
        LOGGER.info("[AntiChatLag] Initialized — chat message signing bypassed to prevent freezes.");
    }
}
