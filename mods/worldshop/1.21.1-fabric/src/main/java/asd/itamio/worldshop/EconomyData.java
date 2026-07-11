package asd.itamio.worldshop;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyData extends SavedData {
    private static final String DATA_NAME = "WorldShopEconomy";
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, UUID> nameToUuid = new HashMap<>();

    public EconomyData() {
    }

    public static EconomyData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new Factory<>(
                EconomyData::new,
                (tag, provider) -> {
                    EconomyData data = new EconomyData();
                    data.load(tag);
                    return data;
                },
                net.minecraft.util.datafix.DataFixTypes.LEVEL
        ), DATA_NAME);
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

    private void load(CompoundTag nbt) {
        balances.clear();
        nameToUuid.clear();

        CompoundTag balTag = nbt.getCompound("Balances");
        for (String key : balTag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                double amount = balTag.getDouble(key);
                balances.put(uuid, amount);
            } catch (IllegalArgumentException ignored) {
            }
        }

        CompoundTag nameTag = nbt.getCompound("NameMap");
        for (String key : nameTag.getAllKeys()) {
            try {
                String uuidStr = nameTag.getString(key);
                UUID uuid = UUID.fromString(uuidStr);
                nameToUuid.put(key.toLowerCase(), uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag compound, HolderLookup.Provider provider) {
        CompoundTag balTag = new CompoundTag();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            balTag.putDouble(entry.getKey().toString(), entry.getValue());
        }
        compound.put("Balances", balTag);

        CompoundTag nameTag = new CompoundTag();
        for (Map.Entry<String, UUID> entry : nameToUuid.entrySet()) {
            nameTag.putString(entry.getKey(), entry.getValue().toString());
        }
        compound.put("NameMap", nameTag);

        return compound;
    }
}
