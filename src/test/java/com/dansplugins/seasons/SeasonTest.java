package com.dansplugins.seasons;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SeasonTest {

    @Test
    void seasonsRunInCalendarOrder() {
        assertEquals(Season.SUMMER, Season.SPRING.next());
        assertEquals(Season.AUTUMN, Season.SUMMER.next());
        assertEquals(Season.WINTER, Season.AUTUMN.next());
    }

    @Test
    void winterIsFollowedBySpring() {
        assertEquals(Season.SPRING, Season.WINTER.next());
    }

    @Test
    void keyIsLowerCaseName() {
        assertEquals("autumn", Season.AUTUMN.key());
    }

    @Test
    void keysListsEverySeasonInCalendarOrder() {
        assertEquals(List.of("spring", "summer", "autumn", "winter"), Season.keys());
    }

    @Test
    void fromKeyIgnoresCaseAndSurroundingSpace() {
        assertEquals(Season.WINTER, Season.fromKey(" Winter "));
    }

    @Test
    void fromKeyReturnsNullForUnknownOrMissingKey() {
        assertNull(Season.fromKey("fall"));
        assertNull(Season.fromKey(null));
    }
}
