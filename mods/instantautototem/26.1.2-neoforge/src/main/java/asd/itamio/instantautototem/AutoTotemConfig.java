package asd.itamio.instantautototem;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class AutoTotemConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<Boolean> ENABLE_AUTO_TOTEM;
    private static final ModConfigSpec.ConfigValue<Boolean> SHOW_MESSAGES;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("General settings").push("general");
        ENABLE_AUTO_TOTEM = BUILDER
                .comment("Enable or disable the auto totem feature")
                .define("enableAutoTotem", true);
        BUILDER.pop();

        BUILDER.comment("Message settings").push("messages");
        SHOW_MESSAGES = BUILDER
                .comment("Show chat messages when totem is equipped")
                .define("showMessages", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public boolean enableAutoTotem;
    public boolean showMessages;

    /** Default constructor used when config loading fails */
    public AutoTotemConfig() {
        this.enableAutoTotem = true;
        this.showMessages = true;
    }

    public static void init(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(AutoTotemConfig::onLoad);
        modEventBus.addListener(AutoTotemConfig::onReload);

        // Register config via the mod container
        container.registerConfig(ModConfig.Type.COMMON, SPEC);

        // Bake initial values
        bake();
    }

    private static void onLoad(final ModConfigEvent.Loading configEvent) {
        bake();
    }

    private static void onReload(final ModConfigEvent.Reloading configEvent) {
        bake();
    }

    public static void bakeInto(AutoTotemConfig instance) {
        try {
            instance.enableAutoTotem = ENABLE_AUTO_TOTEM.get();
            instance.showMessages = SHOW_MESSAGES.get();
        } catch (Exception e) {
            System.err.println("[MODAPP-ERROR] Failed to bake config: " + e.getMessage());
            e.printStackTrace();
            instance.enableAutoTotem = true;
            instance.showMessages = true;
        }
    }

    private static void bake() {
        if (InstantAutoTotem.config != null) {
            bakeInto(InstantAutoTotem.config);
        }
    }
}
