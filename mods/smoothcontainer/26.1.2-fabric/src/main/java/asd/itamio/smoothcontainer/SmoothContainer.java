package asd.itamio.smoothcontainer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmoothContainer implements ClientModInitializer {

    public static final String MOD_ID = "smoothcontainer";
    public static final String MOD_NAME = "SmoothContainer (26.1.2 Fabric)";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info(ModInfoPrinter.build(MOD_NAME, VERSION));
        LOGGER.info("SmoothContainer initialized - container GUI stutter mitigation active");

        ClientTickEvents.END_CLIENT_TICK.register(ContainerOpenHandler::onClientTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ContainerOpenHandler.cleanup());
    }
}
