package com.baseline.timetrack.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GeoPoint {

    private Double latitude;
    private Double longitude;

    public static GeoPoint of(Double latitude, Double longitude) {
        return latitude == null || longitude == null ? null : new GeoPoint(latitude, longitude);
    }

    /** Great-circle distance in meters (haversine). */
    public double distanceTo(GeoPoint other) {
        double earthRadius = 6_371_000;
        double dLat = Math.toRadians(other.latitude - latitude);
        double dLng = Math.toRadians(other.longitude - longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(other.latitude))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
