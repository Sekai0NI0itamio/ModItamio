package asd.itamio.tntduper;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TNTDuper.MOD_ID)
public class TNTDuper {

    public static final String MOD_ID = "tnt_duper";
    public static final String MOD_NAME = "TNT Duper";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TNTDuper(IEventBus modEventBus) {
        ModInfoPrinter.print(LOGGER::info, MOD_NAME, VERSION);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        try {
            event.enqueueWork(() -> {
                DispenserBlock.registerBehavior(Items.TNT, new BehaviorDispenseTNTDuper());
            });
            LOGGER.info("Registered TNT duper dispense behavior for TNT");
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to register TNT duper dispense behavior: " + e.getMessage());
            e.printStackTrace();
        }
        LOGGER.info("TNT Duper initialized — dispensers will duplicate TNT without consuming the item");
    }
}
