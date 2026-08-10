package asd.itamio.createtnt;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * SimpleNetworkWrapper for the mod. Carries the one packet that tells clients
 * where to render the explosion particles (and their knockback).
 */
public class ModNetwork {

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel("createtnt");

    public static void register() {
        NETWORK.registerMessage(SpawnExplosionMessage.Handler.class, SpawnExplosionMessage.class, 0, Side.CLIENT);
    }
}
