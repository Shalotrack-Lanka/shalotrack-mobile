package com.example.letstracklanka.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * "Where does this vehicle usually go?" Same rules as the Fleet web portal (FrequentPlaces.php):
 * every finished trip contributes its END point as one visit; visits closer than 150 m to an existing group
 * join it (running centre), so parking a few metres differently each day is still one place; a place seen
 * only once is not "frequent"; the busiest 15 are kept. Pure Java, no Android types, so it is unit-tested.
 */
public final class FrequentPlaces {

    public static final double RADIUS_M = 150;
    public static final int MIN_VISITS = 2;
    public static final int MAX_PLACES = 15;

    private FrequentPlaces() {}

    /** One finished trip's end point. {@code endIso} is the trip's ISO end time (used only to find the latest). */
    public static final class Visit {
        public final double lat, lng;
        public final String endIso;
        public final int minutes;

        public Visit(double lat, double lng, String endIso, int minutes) {
            this.lat = lat; this.lng = lng; this.endIso = endIso == null ? "" : endIso; this.minutes = minutes;
        }
    }

    /** A named spot from the customer's saved places, used only to label a result. */
    public static final class Saved {
        public final String name;
        public final double lat, lng;

        public Saved(String name, double lat, double lng) {
            this.name = name; this.lat = lat; this.lng = lng;
        }
    }

    public static final class Place {
        public double lat, lng;
        public int visits;
        public String lastVisitIso = "";
        public int totalMinutes;
        public String savedName;   // null when it is not one of the saved places
    }

    public static List<Place> build(List<Visit> visits, List<Saved> saved) {
        List<Place> groups = new ArrayList<>();
        if (visits != null) {
            for (Visit v : visits) {
                if (!validCoordinate(v.lat, v.lng)) continue;     // bad GPS fix or in-progress trip
                Place hit = null;
                for (Place g : groups) {
                    if (distanceMeters(v.lat, v.lng, g.lat, g.lng) <= RADIUS_M) { hit = g; break; }
                }
                if (hit == null) {
                    Place p = new Place();
                    p.lat = v.lat; p.lng = v.lng; p.visits = 1; p.totalMinutes = Math.max(0, v.minutes);
                    p.lastVisitIso = v.endIso;
                    groups.add(p);
                } else {
                    int n = hit.visits;
                    hit.lat = (hit.lat * n + v.lat) / (n + 1);
                    hit.lng = (hit.lng * n + v.lng) / (n + 1);
                    hit.visits++;
                    hit.totalMinutes += Math.max(0, v.minutes);
                    if (v.endIso.compareTo(hit.lastVisitIso) > 0) hit.lastVisitIso = v.endIso;
                }
            }
        }

        List<Place> kept = new ArrayList<>();
        for (Place g : groups) if (g.visits >= MIN_VISITS) kept.add(g);
        Collections.sort(kept, (a, b) -> a.visits != b.visits
                ? Integer.compare(b.visits, a.visits) : Integer.compare(b.totalMinutes, a.totalMinutes));
        if (kept.size() > MAX_PLACES) kept = new ArrayList<>(kept.subList(0, MAX_PLACES));

        if (saved != null) {
            for (Place p : kept) {
                for (Saved s : saved) {
                    if (s.name != null && distanceMeters(p.lat, p.lng, s.lat, s.lng) <= RADIUS_M) {
                        p.savedName = s.name;
                        break;
                    }
                }
            }
        }
        return kept;
    }

    public static boolean validCoordinate(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng)
                && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                && !(lat == 0 && lng == 0);          // "null island": the GPS had no fix
    }

    /** Haversine distance in metres. */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
