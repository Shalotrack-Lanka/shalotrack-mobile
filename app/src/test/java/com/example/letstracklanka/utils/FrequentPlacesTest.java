package com.example.letstracklanka.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class FrequentPlacesTest {

    private static FrequentPlaces.Visit v(double lat, double lng, String end, int min) {
        return new FrequentPlaces.Visit(lat, lng, end, min);
    }

    @Test public void nearbyVisitsMergeAndSingleVisitsAreDropped() {
        List<FrequentPlaces.Place> out = FrequentPlaces.build(Arrays.asList(
                v(6.9271, 79.8612, "2026-10-01T10:00:00Z", 20),
                v(6.92715, 79.86125, "2026-10-03T10:00:00Z", 30),      // ~7 m away: same place
                v(7.2906, 80.6337, "2026-10-02T10:00:00Z", 15)),       // seen once: dropped
                null);
        assertEquals(1, out.size());
        assertEquals(2, out.get(0).visits);
        assertEquals(50, out.get(0).totalMinutes);
        assertEquals("2026-10-03T10:00:00Z", out.get(0).lastVisitIso);
    }

    @Test public void farApartVisitsStaySeparateAndSortedByVisits() {
        List<FrequentPlaces.Place> out = FrequentPlaces.build(Arrays.asList(
                v(6.9, 79.8, "a", 5), v(6.9, 79.8, "b", 5),
                v(7.3, 80.6, "c", 5), v(7.3, 80.6, "d", 5), v(7.3, 80.6, "e", 5)), null);
        assertEquals(2, out.size());
        assertEquals(3, out.get(0).visits);
        assertEquals(2, out.get(1).visits);
    }

    @Test public void badFixesAreIgnored() {
        List<FrequentPlaces.Place> out = FrequentPlaces.build(Arrays.asList(
                v(0, 0, "a", 5), v(0, 0, "b", 5), v(200, 10, "c", 5), v(200, 10, "d", 5),
                v(Double.NaN, 1, "e", 5), v(Double.NaN, 1, "f", 5)), null);
        assertEquals(0, out.size());
    }

    @Test public void savedPlaceLabelsNearbyResult() {
        List<FrequentPlaces.Place> out = FrequentPlaces.build(
                Arrays.asList(v(6.9, 79.8, "a", 5), v(6.9, 79.8, "b", 5)),
                Arrays.asList(new FrequentPlaces.Saved("Home", 6.9005, 79.8005), new FrequentPlaces.Saved("Office", 7.3, 80.6)));
        assertEquals("Home", out.get(0).savedName);
        List<FrequentPlaces.Place> none = FrequentPlaces.build(
                Arrays.asList(v(6.9, 79.8, "a", 5), v(6.9, 79.8, "b", 5)),
                Arrays.asList(new FrequentPlaces.Saved("Office", 7.3, 80.6)));
        assertNull(none.get(0).savedName);
    }

    @Test public void capsAtFifteenPlaces() {
        FrequentPlaces.Visit[] vs = new FrequentPlaces.Visit[40];
        for (int i = 0; i < 20; i++) {                       // 20 distinct places, 2 visits each
            vs[i * 2] = v(6.0 + i * 0.05, 79.0, "x", 1);
            vs[i * 2 + 1] = v(6.0 + i * 0.05, 79.0, "y", 1);
        }
        assertEquals(15, FrequentPlaces.build(Arrays.asList(vs), null).size());
    }

    @Test public void distanceIsAboutRight() {
        // Colombo Fort to Kandy is roughly 94 km in a straight line
        double d = FrequentPlaces.distanceMeters(6.9344, 79.8428, 7.2906, 80.6337);
        assertEquals(true, d > 90000 && d < 100000);
    }
}
