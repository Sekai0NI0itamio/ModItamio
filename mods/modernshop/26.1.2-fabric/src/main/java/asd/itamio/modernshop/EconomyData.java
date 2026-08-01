package asd.itamio.modernshop;

import com.mojang.datafixers.DataFix;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.PrimitiveCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class EconomyData extends SavedData implements EconomyProvider {
    private static final String DATA_NAME = "modernshop_economy";
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, UUID> nameToUuid = new HashMap<>();

    public static final Codec<EconomyData> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.unboundedMap(UUIDUtil.CODEC, Codec.DOUBLE)
                .fieldOf("balances")
                .forGetter(data -> data.balances),
            Codec.unboundedMap(Codec.STRING, UUIDUtil.CODEC)
                .fieldOf("name_to_uuid")
                .forGetter(data -> data.nameToUuid)
        ).apply(instance, (balances, nameToUuid) -> {
            EconomyData data = new EconomyData();
            data.balances.putAll(balances);
            data.nameToUuid.putAll(nameToUuid);
            return data;
        })
    );

    public static final SavedDataType<EconomyData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(ModernShop.MOD_ID, "economy"), EconomyData::new, CODEC, DataFixTypes.SAVED_DATA_MAP_DATA
    );

    public static EconomyData get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return new EconomyData();
        }
        return serverLevel.getDataStorage().computeIfAbsent(TYPE);
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

    // ========== EconomyProvider interface methods ==========

    @Override
    public double getBalance(ServerLevel level, UUID player) {
        return getBalance(player);
    }

    @Override
    public void setBalance(ServerLevel level, UUID player, double amount) {
        setBalance(player, amount);
    }

    @Override
    public void addBalance(ServerLevel level, UUID player, double amount) {
        addBalance(player, amount);
    }

    @Override
    public boolean subtractBalance(ServerLevel level, UUID player, double amount) {
        return subtractBalance(player, amount);
    }

    @Override
    public void registerPlayer(ServerLevel level, String name, UUID uuid) {
        registerPlayer(name, uuid);
    }

    @Override
    public UUID getUuidByName(ServerLevel level, String name) {
        return getUuidByName(name);
    }
}
