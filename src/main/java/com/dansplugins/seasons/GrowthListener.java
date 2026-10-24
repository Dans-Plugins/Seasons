package com.dansplugins.seasons;

import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Applies the current season's {@link GrowthPolicy} to vanilla growth steps. {@code randomTickSpeed}
 * is never touched, so ore blocks, fire spread and other random-tick behaviour stay vanilla.
 */
final class GrowthListener implements Listener {

    private final GrowthPolicy policy;
    private final Supplier<Season> currentSeason;
    private final DoubleSupplier random;

    GrowthListener(GrowthPolicy policy, Supplier<Season> currentSeason, DoubleSupplier random) {
        this.policy = policy;
        this.currentSeason = currentSeason;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        int stages = policy.stagesFor(currentSeason.get(), random.getAsDouble());
        if (stages == 0) {
            event.setCancelled(true);
            return;
        }
        if (stages == 1) {
            return;
        }
        BlockState newState = event.getNewState();
        BlockData grown = withExtraStages(event.getBlock().getBlockData(), newState.getBlockData(),
                stages - 1);
        if (grown != null) {
            newState.setBlockData(grown);
        }
    }

    /**
     * Ages the result of a growth step by extra stages, never past fully grown.
     *
     * <p>Extra stages only apply to a crop ageing in place, where the block was already ageable.
     * Growth that places a new block (sugar cane, cactus, a melon beside its stem) grows into air,
     * so it keeps its vanilla single step.
     *
     * @param before the block's data before the step
     * @param after the block's data the step would set
     * @return the aged data, or {@code null} if the step should be left as it is
     */
    static BlockData withExtraStages(BlockData before, BlockData after, int extraStages) {
        if (!(before instanceof Ageable) || !(after instanceof Ageable)) {
            return null;
        }
        Ageable ageable = (Ageable) after;
        int extra = Math.min(extraStages, ageable.getMaximumAge() - ageable.getAge());
        if (extra <= 0) {
            return null;
        }
        ageable.setAge(ageable.getAge() + extra);
        return ageable;
    }
}
