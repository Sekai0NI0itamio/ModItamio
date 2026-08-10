package asd.itamio.createtnt;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
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

    public static class Handler implements IMessageHandler<SpawnExplosionMessage, IMessage> {
        @Override
        public IMessage onMessage(final SpawnExplosionMessage message, MessageContext ctx) {
            // Apply knockback to the local player, then spawn the blast
            // wave + explosion cloud particles.
            Minecraft.getMinecraft().addScheduledTask(() -> {
                EntityPlayer player = Minecraft.getMinecraft().player;
                if (player == null) {
                    return;
                }
                player.motionX += message.knockbackX;
                player.motionY += message.knockbackY;
                player.motionZ += message.knockbackZ;
                ShellExplosionEffects.spawnShellExplosion(message.x, message.y, message.z,
                    message.power, message.isPlume);
            });
            return null;
        }
    }
}
