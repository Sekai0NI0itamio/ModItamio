package asd.itamio.givemorethan64;

import asd.itamio.ModInfoPrinter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(Givemorethan64.MOD_ID)
public class Givemorethan64 {

    public static final String MOD_ID = "givemorethan64";
    public static final String MOD_NAME = "give more than 64";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Givemorethan64(IEventBus modEventBus) {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);

        // Register the command handler on the Forge event bus
        MinecraftForge.EVENT_BUS.register(new CommandGiveHandler());

        LOGGER.info("GiveMoreThan64 initialized - /give command now supports amounts above stack limit");
    }
}
