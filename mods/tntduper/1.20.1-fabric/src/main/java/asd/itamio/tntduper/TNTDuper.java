package asd.itamio.tntduper;

import net.fabricmc.api.ModInitializer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TNTDuper implements ModInitializer {

    public static final String MOD_ID = "tnt_duper";
    public static final String MOD_NAME = "TNT Duper";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        try {
            DispenserBlock.registerBehavior(Items.TNT, new BehaviorDispenseTNTDuper());
            LOGGER.info("Registered TNT duper dispense behavior for TNT");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register TNT duper dispense behavior: " + e.getMessage());
            e.printStackTrace();
        }
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("TNT Duper initialized — dispensers will duplicate TNT without consuming the item");
    }
}
