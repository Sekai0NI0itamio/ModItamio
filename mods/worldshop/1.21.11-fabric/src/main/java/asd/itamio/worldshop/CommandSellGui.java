package asd.itamio.worldshop;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandSellGui {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sellgui")
                .executes(CommandSellGui::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can use this command."));
            return 0;
        }

        source.getServer().execute(() -> {
            ShopPayload.ShopMessage msg = ShopPayload.ShopMessage.openSellGui();
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new ShopPayload(msg));
        });
        return 1;
    }
}
