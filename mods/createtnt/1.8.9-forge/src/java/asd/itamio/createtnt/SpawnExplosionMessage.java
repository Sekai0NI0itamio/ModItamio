package asd.itamio.createtnt;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Tells the client to render the explosion at a given position.
 *
 * <p>The packet carries the explosion {@code power} (particle sizing scale)
 * and the receiving player's knockback vector, which the client applies
 * directly to its own player entity (player motion is client-authoritative).</p>
 */
public class SpawnExplosionMessage implements IMessage {

    private double x, y, z;
    private float power;
    private boolean isPlume;
    private float knockbackX, knockbackY, knockbackZ;

    /** No-arg constructor required by the reflective SimpleNetworkWrapper decoder. */
    public SpawnExplosionMessage() {
    }

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

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.power = buf.readFloat();
        this.isPlume = buf.readBoolean();
        this.knockbackX = buf.readFloat();
        this.knockbackY = buf.readFloat();
        this.knockbackZ = buf.readFloat();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeDouble(this.x);
        buf.writeDouble(this.y);
        buf.writeDouble(this.z);
        buf.writeFloat(this.power);
        buf.writeBoolean(this.isPlume);
        buf.writeFloat(this.knockbackX);
        buf.writeFloat(this.knockbackY);
        buf.writeFloat(this.knockbackZ);
    }

    /**
     * Static handler. Schedules the client-side rendering work on the main
     * thread so we don't touch Minecraft state from the network thread.
     */
    public static class Handler implements IMessageHandler<SpawnExplosionMessage, IMessage> {
        @Override
        public IMessage onMessage(final SpawnExplosionMessage msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> ShellExplosionEffects.handlePacket(msg));
            return null;
        }
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
