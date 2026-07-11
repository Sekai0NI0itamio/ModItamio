package asd.itamio.worldshop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyData extends SavedData {
    private static final Identifier DATA_ID = Identifier.fromNamespaceAndPath("worldshop", "economy");

    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, UUID> nameToUuid = new HashMap<>();

    // Codec for serialization - declared before TYPE to avoid forward reference
    private static final Codec<EconomyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.CODEC, Codec.DOUBLE)
                    .fieldOf("Balances")
                    .forGetter(data -> data.balances),
            Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC)
                    .fieldOf("NameMap")
                    .forGetter(data -> data.nameToUuid)
    ).apply(instance, (balances, nameMap) -> {
        EconomyData data = new EconomyData();
        data.balances.putAll(balances);
        data.nameToUuid.putAll(nameMap);
        return data;
    }));

    public static final SavedDataType<EconomyData> TYPE = new SavedDataType<>(
            DATA_ID,
            EconomyData::new,
            CODEC
    );

    private EconomyData() {
    }

    public static EconomyData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void setBalance(UUID uuid, double amount) {
        balances.put(uuid, amount);
        setDirty();
    }

    public void addBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        setBalance(uuid, current + amount);
    }

    public boolean subtractBalance(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current >= amount) {
            setBalance(uuid, current - amount);
            return true;
        }
        return false;
    }

    public void registerPlayer(String name, UUID uuid) {
        nameToUuid.put(name.toLowerCase(), uuid);
        setDirty();
    }

    public UUID getUuidByName(String name) {
        return nameToUuid.get(name.toLowerCase());
    }
}
