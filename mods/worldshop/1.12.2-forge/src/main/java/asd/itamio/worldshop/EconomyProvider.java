package asd.itamio.worldshop;

import net.minecraft.world.World;

import java.util.UUID;

/**
 * Economy abstraction interface for World Shop.
 * Other mods can register their own provider via
 * {@link WorldShop#setEconomyProvider(EconomyProvider)} to use their own
 * economy system instead of the built-in EconomyData.
 */
public interface EconomyProvider {
    double getBalance(World level, UUID player);
    void setBalance(World level, UUID player, double amount);
    void addBalance(World level, UUID player, double amount);
    boolean subtractBalance(World level, UUID player, double amount);
    void registerPlayer(World level, String name, UUID uuid);
    UUID getUuidByName(World level, String name);
}
