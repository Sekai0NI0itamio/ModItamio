package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class CommandShopReset {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
                .then(Commands.literal("reset")
                        .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            WorldShop.getPriceEngine().clearCache();
                            PriceConfig priceConfig = WorldShop.getPriceEngine().getPriceConfig();
                            if (priceConfig != null) {
                                priceConfig.clearAllPrices();
                            }
                            WorldShop.buildShopCategories();
                            WorldShop.applyPersistedCategoryOrder();
                            source.sendSuccess(() -> Component.literal("\u00a7aShop prices have been reset and recalculated from recipes!"), true);
                            WorldShop.LOGGER.info("Shop prices reset by {} (config file cleared)", source.getTextName());
                            return 1;
                        })
                )
        );
    }
}
