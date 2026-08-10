package asd.itamio.createtnt;

import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * SimpleNetworkWrapper channel for the mod. Registers the one packet that
 * tells clients where to render the explosion particles.
 */
public class ModNetwork {

    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(BetterTNTs.MOD_ID);

    private static int nextId = 0;

    public static void register() {
        NETWORK.registerMessage(SpawnExplosionMessage.Handler.class, SpawnExplosionMessage.class, nextId++, Side.CLIENT);
    }
}