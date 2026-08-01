package asd.itamio.modernshop;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

public class CommandShop {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("shop")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    if (source.getEntity() instanceof ServerPlayer player) {
                        ModernShop.NETWORK.send(
                                PacketDistributor.PLAYER.with(() -> player),
                                ShopPacket.openShop()
                        );
                    } else {
                        source.sendFailure(Component.literal("\u00a7cOnly players can use this command."));
                    }
                    return 1;
                })
        );
    }
}
