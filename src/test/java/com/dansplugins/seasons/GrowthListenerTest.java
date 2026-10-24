package com.dansplugins.seasons;

import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.block.BlockGrowEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests run without a server, so {@code Block} and {@code BlockState} cannot be faked (loading them
 * initialises Bukkit registries). Events here carry no block; the paths that reach the block are
 * covered through {@link GrowthListener#withExtraStages}.
 */
class GrowthListenerTest {

    /** A crop's block data, holding just enough state to observe an age change. */
    private static final class Crop {
        int age;
        final int maximumAge;

        Crop(int age, int maximumAge) {
            this.age = age;
            this.maximumAge = maximumAge;
        }

        Ageable asAgeable() {
            return (Ageable) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] {Ageable.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getAge":
                                return age;
                            case "getMaximumAge":
                                return maximumAge;
                            case "setAge":
                                age = (Integer) args[0];
                                return null;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }

    /** Block data with no age, such as air. */
    private static BlockData plainData() {
        return (BlockData) Proxy.newProxyInstance(GrowthListenerTest.class.getClassLoader(),
                new Class<?>[] {BlockData.class}, (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static GrowthListener listener(Season season, double multiplier, double roll) {
        Map<Season, Double> multipliers = new EnumMap<>(Season.class);
        for (Season each : Season.values()) {
            multipliers.put(each, 1.0);
        }
        multipliers.put(season, multiplier);
        return new GrowthListener(new GrowthPolicy(multipliers), () -> season, () -> roll);
    }

    @Test
    void cancelsGrowthWhenPolicyGivesNoStages() {
        BlockGrowEvent event = new BlockGrowEvent(null, null);

        listener(Season.WINTER, 0.25, 0.9).onBlockGrow(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void leavesVanillaGrowthUntouched() {
        BlockGrowEvent event = new BlockGrowEvent(null, null);

        listener(Season.WINTER, 0.25, 0.1).onBlockGrow(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void usesTheCurrentSeasonsMultiplier() {
        BlockGrowEvent event = new BlockGrowEvent(null, null);

        listener(Season.AUTUMN, 0.0, 0.0).onBlockGrow(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void extraStagesAgeACropInPlace() {
        Crop crop = new Crop(3, 7);
        Ageable after = crop.asAgeable();

        BlockData grown = GrowthListener.withExtraStages(new Crop(2, 7).asAgeable(), after, 2);

        assertSame(after, grown);
        assertEquals(5, crop.age);
    }

    @Test
    void extraStagesStopAtMaximumAge() {
        Crop crop = new Crop(6, 7);

        BlockData grown = GrowthListener.withExtraStages(new Crop(5, 7).asAgeable(),
                crop.asAgeable(), Integer.MAX_VALUE);

        assertEquals(7, crop.age);
        assertTrue(grown != null);
    }

    @Test
    void extraStagesLeaveAFullyGrownCropAlone() {
        Crop crop = new Crop(7, 7);

        assertNull(GrowthListener.withExtraStages(new Crop(6, 7).asAgeable(), crop.asAgeable(), 2));
        assertEquals(7, crop.age);
    }

    @Test
    void extraStagesDoNotApplyToGrowthIntoANewBlock() {
        Crop cane = new Crop(0, 15);

        assertNull(GrowthListener.withExtraStages(plainData(), cane.asAgeable(), 2));
        assertEquals(0, cane.age);
    }

    @Test
    void extraStagesDoNotApplyWhenTheResultHasNoAge() {
        assertNull(GrowthListener.withExtraStages(new Crop(3, 3).asAgeable(), plainData(), 2));
    }
}
