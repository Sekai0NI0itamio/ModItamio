package asd.itamio.createtnt;

import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.item.PrimedTnt;

/** Schedules primed TNTs caught in a blast, honouring the chain config. */
public final class ChainDetonation {

    private ChainDetonation() {
    }

    /**
     * Sets fuses on the primed TNTs caught by a blast. Simultaneous mode
     * fuses everything at once; sequential mode staggers each TNT by
     * {@link ModConfig#chainDetonationDelay} ticks, nearest first, so the
     * server is not hit by one giant explosion tick. Capped by
     * {@link ModConfig#maxChainDetonations}.
     */
    public static void detonate(List<PrimedTnt> tnts, double x, double y, double z) {
        if (tnts.isEmpty()) {
            return;
        }
        tnts.sort(Comparator.comparingDouble(tnt -> tnt.distanceToSqr(x, y, z)));
        int cap = ModConfig.maxChainDetonations;
        int index = 0;
        for (PrimedTnt tnt : tnts) {
            if (cap > 0 && index >= cap) {
                break;
            }
            int fuse = ModConfig.simultaneousChainDetonation
                ? 0
                : Math.max(1, ModConfig.chainDetonationDelay * (index + 1));
            tnt.setFuse(fuse);
            index++;
        }
    }
}
