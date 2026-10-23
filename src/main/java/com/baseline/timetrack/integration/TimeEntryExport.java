package com.baseline.timetrack.integration;

import java.time.Instant;

import com.baseline.timetrack.entity.GeoPoint;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.entity.TimerType;

/** Provider-neutral closed time entry, sent to connectors and returned by the inbound export API. */
public record TimeEntryExport(
        Long id,
        String provider,
        String taskExternalId,
        String userExternalId,
        String userEmail,
        TimerType type,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds,
        String notes,
        Coordinates startLocation,
        Coordinates endLocation,
        Instant leftSiteAt,
        Instant updatedAt) {

    public record Coordinates(double latitude, double longitude) {
        static Coordinates of(GeoPoint p) {
            return p == null ? null : new Coordinates(p.getLatitude(), p.getLongitude());
        }
    }

    public static TimeEntryExport of(TimerEntry t, String userExternalId) {
        return new TimeEntryExport(
                t.getId(),
                t.getTask().getSource(),
                t.getTask().getExternalId(),
                userExternalId,
                t.getUser().getEmail(),
                t.getType(),
                t.getStartTime(),
                t.getEndTime(),
                t.getDurationSeconds(),
                t.getNotes(),
                Coordinates.of(t.getStartLocation()),
                Coordinates.of(t.getEndLocation()),
                t.getLeftSiteAt(),
                t.getUpdatedAt());
    }
}
