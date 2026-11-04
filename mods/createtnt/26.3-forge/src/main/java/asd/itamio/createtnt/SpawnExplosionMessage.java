package asd.itamio.createtnt;

import net.minecraft.network.FriendlyByteBuf;

public record SpawnExplosionMessage(double x, double y, double z, float power, boolean isPlume,
                                    float knockbackX, float knockbackY, float knockbackZ,
                                    float axisX, float axisY, float axisZ) {

    public static void encode(SpawnExplosionMessage msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeFloat(msg.power);
        buf.writeBoolean(msg.isPlume);
        buf.writeFloat(msg.knockbackX);
        buf.writeFloat(msg.knockbackY);
        buf.writeFloat(msg.knockbackZ);
        buf.writeFloat(msg.axisX);
        buf.writeFloat(msg.axisY);
        buf.writeFloat(msg.axisZ);
    }

    public static SpawnExplosionMessage decode(FriendlyByteBuf buf) {
        return new SpawnExplosionMessage(
            buf.readDouble(), buf.readDouble(), buf.readDouble(),
            buf.readFloat(), buf.readBoolean(),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
