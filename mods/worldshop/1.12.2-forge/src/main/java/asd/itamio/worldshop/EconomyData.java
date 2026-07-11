package asd.itamio.worldshop;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EconomyData extends WorldSavedData {
    private static final String DATA_NAME = "WorldShopEconomy";
    private final Map<UUID, Double> balances = new HashMap<>();
    private final Map<String, UUID> nameToUuid = new HashMap<>();

    public EconomyData() {
        super(DATA_NAME);
    }

    public EconomyData(String name) {
        super(name);
    }

    public static EconomyData get(World world) {
        EconomyData data = (EconomyData) world.getPerWorldStorage().getOrLoadData(EconomyData.class, DATA_NAME);
        if (data == null) {
            data = new EconomyData();
            world.getPerWorldStorage().setData(DATA_NAME, data);
        }
        return data;
    }

    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public void setBalance(UUID uuid, double amount) {
        balances.put(uuid, amount);
        markDirty();
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
        markDirty();
    }

    public UUID getUuidByName(String name) {
        return nameToUuid.get(name.toLowerCase());
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        balances.clear();
        nameToUuid.clear();

        NBTTagCompound balTag = nbt.getCompoundTag("Balances");
        for (String key : balTag.getKeySet()) {
            try {
                UUID uuid = UUID.fromString(key);
                double amount = balTag.getDouble(key);
                balances.put(uuid, amount);
            } catch (IllegalArgumentException ignored) {
            }
        }

        NBTTagCompound nameTag = nbt.getCompoundTag("NameMap");
        for (String key : nameTag.getKeySet()) {
            try {
                String uuidStr = nameTag.getString(key);
                UUID uuid = UUID.fromString(uuidStr);
                nameToUuid.put(key.toLowerCase(), uuid);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagCompound balTag = new NBTTagCompound();
        for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
            balTag.setDouble(entry.getKey().toString(), entry.getValue());
        }
        compound.setTag("Balances", balTag);

        NBTTagCompound nameTag = new NBTTagCompound();
        for (Map.Entry<String, UUID> entry : nameToUuid.entrySet()) {
            nameTag.setString(entry.getKey(), entry.getValue().toString());
        }
        compound.setTag("NameMap", nameTag);

        return compound;
    }
}
