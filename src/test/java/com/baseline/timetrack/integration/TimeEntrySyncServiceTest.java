package com.baseline.timetrack.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.baseline.timetrack.entity.GeoPoint;

class TimeEntrySyncServiceTest {

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertThat(TimeEntrySyncService.backoff(1)).isEqualTo(Duration.ofMinutes(2));
        assertThat(TimeEntrySyncService.backoff(3)).isEqualTo(Duration.ofMinutes(8));
        assertThat(TimeEntrySyncService.backoff(50)).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void haversineDistance() {
        // Zócalo to Ángel de la Independencia, about 3.4 km.
        double meters = GeoPoint.of(19.4326, -99.1332).distanceTo(GeoPoint.of(19.4270, -99.1677));
        assertThat(meters).isBetween(3_500.0, 3_700.0);
    }
}
