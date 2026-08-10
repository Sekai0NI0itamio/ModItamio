package asd.itamio.createtnt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells the client to render the explosion at a given position.
 *
 * <p>The packet carries the explosion {@code power} (particle sizing scale)
 * and the receiving player's knockback vector, which the client applies
 * directly to its own player entity (player motion is client-authoritative).</p>
 */
public class SpawnExplosionMessage {

    private final double x, y, z;
    private final float power;
    private final boolean isPlume;
    private final float knockbackX, knockbackY, knockbackZ;

    public SpawnExplosionMessage(double x, double y, double z, float power, boolean isPlume,
                                 float knockbackX, float knockbackY, float knockbackZ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.power = power;
        this.isPlume = isPlume;
        this.knockbackX = knockbackX;
        this.knockbackY = knockbackY;
        this.knockbackZ = knockbackZ;
    }

    public SpawnExplosionMessage(FriendlyByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.power = buf.readFloat();
        this.isPlume = buf.readBoolean();
        this.knockbackX = buf.readFloat();
        this.knockbackY = buf.readFloat();
        this.knockbackZ = buf.readFloat();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.power);
        buf.writeBoolean(this.isPlume);
        buf.writeFloat(this.knockbackX);
        buf.writeFloat(this.knockbackY);
        buf.writeFloat(this.knockbackZ);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            // Client-only: apply knockback to the local player, then spawn the
            // blast wave + explosion cloud particles.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ShellExplosionEffects.handlePacket(this)));
        ctx.get().setPacketHandled(true);
    }

    public double x() { return this.x; }
    public double y() { return this.y; }
    public double z() { return this.z; }
    public float power() { return this.power; }
    public boolean isPlume() { return this.isPlume; }
    public float knockbackX() { return this.knockbackX; }
    public float knockbackY() { return this.knockbackY; }
    public float knockbackZ() { return this.knockbackZ; }
}
