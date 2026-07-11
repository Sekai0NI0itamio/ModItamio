package asd.itamio.givemorethan64;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Givemorethan64.MOD_ID)
public class Givemorethan64 {

    public static final String MOD_ID = "givemorethan64";
    public static final String MOD_NAME = "give more than 64";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public Givemorethan64(IEventBus modEventBus, Dist dist, ModContainer container) {
        // Register server event handler to intercept /give command
        NeoForge.EVENT_BUS.register(new CommandGiveHandler());
        LOGGER.info("GiveMoreThan64 event handlers registered");

        // Print the info card
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("GiveMoreThan64 initialized - /give command now supports unlimited amounts above stack limit");
    }
}
