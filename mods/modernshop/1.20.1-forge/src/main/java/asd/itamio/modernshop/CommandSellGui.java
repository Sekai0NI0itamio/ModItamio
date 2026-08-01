package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class CommandSellGui {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sellgui")
                .requires(source -> source.getEntity() instanceof ServerPlayer)
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (source.getEntity() instanceof ServerPlayer player) {
                        ModernShop.NETWORK.send(
                                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                                ShopPacket.openSellGui()
                        );
                    }
                    return 1;
                })
        );
    }
}
