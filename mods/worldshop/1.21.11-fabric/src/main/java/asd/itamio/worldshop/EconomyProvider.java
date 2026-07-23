package asd.itamio.worldshop;

import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Economy abstraction interface for Modern Shop.
 * Other mods can register their own provider via {@link WorldShop#setEconomyProvider(EconomyProvider)}
 * to use their own economy system instead of the built-in EconomyData.
 */
public interface EconomyProvider {
    double getBalance(ServerLevel level, UUID player);
    void setBalance(ServerLevel level, UUID player, double amount);
    void addBalance(ServerLevel level, UUID player, double amount);
    boolean subtractBalance(ServerLevel level, UUID player, double amount);
    void registerPlayer(ServerLevel level, String name, UUID uuid);
    UUID getUuidByName(ServerLevel level, String name);
}
