package asd.itamio.createtnt;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * SimpleChannel for the mod. Carries the one packet that tells clients where
 * to render the explosion particles (and their knockback).
 */
public class ModNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel NETWORK = NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation(BetterTNTs.MOD_ID, "main"))
        .clientAcceptedVersions(s -> true)
        .serverAcceptedVersions(s -> true)
        .networkProtocolVersion(() -> PROTOCOL_VERSION)
        .simpleChannel();

    public static void register() {
        NETWORK.messageBuilder(SpawnExplosionMessage.class, 0, NetworkDirection.PLAY_TO_CLIENT)
            .encoder(SpawnExplosionMessage::toBytes)
            .decoder(SpawnExplosionMessage::new)
            .consumerMainThread(SpawnExplosionMessage::handle)
            .add();
    }
}
