package asd.itamio.tntduper;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TNTDuper.MOD_ID)
public class TNTDuper {

    public static final String MOD_ID = "tnt_duper";
    public static final String MOD_NAME = "TNT Duper";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public TNTDuper(IEventBus modEventBus) {
        modEventBus.addListener(this::commonSetup);

        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        LOGGER.info("TNT Duper initialized — dispensers will duplicate TNT without consuming the item");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            try {
                DispenserBlock.registerBehavior(Items.TNT, new BehaviorDispenseTNTDuper());
                LOGGER.info("Registered TNT duper dispense behavior for TNT");
            } catch (Exception e) {
                System.err.println("[MODAPP-ERROR] Failed to register TNT duper dispense behavior: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
}
