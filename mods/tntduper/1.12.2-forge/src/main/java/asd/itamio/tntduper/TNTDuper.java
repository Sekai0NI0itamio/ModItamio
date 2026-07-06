package asd.itamio.tntduper;

import net.minecraft.block.BlockDispenser;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = TNTDuper.MOD_ID, name = TNTDuper.MOD_NAME, version = TNTDuper.VERSION)
public class TNTDuper {

    public static final String MOD_ID = "tnt_duper";
    public static final String MOD_NAME = "TNT Duper";
    public static final String VERSION = "1.0.0";
    public static Logger LOGGER;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("TNT Duper initialized — dispensers will duplicate TNT without consuming the item");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        try {
            BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(
                Item.getItemFromBlock(Blocks.TNT),
                new BehaviorDispenseTNTDuper()
            );
            LOGGER.info("Registered TNT duper dispense behavior for TNT block");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register TNT duper dispense behavior: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
