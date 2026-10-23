package com.baseline.timetrack.task;

import java.time.Instant;

import com.baseline.timetrack.entity.Site;
import com.baseline.timetrack.entity.Task;
import com.baseline.timetrack.entity.TaskStatus;

public final class TaskDtos {

    private TaskDtos() {
    }

    /** Includes the geofence so the app can register it with the OS when a timer starts. */
    public record SiteResponse(Long id, String source, String externalId, String name, String address,
                               Double latitude, Double longitude, Integer radiusMeters) {
        public static SiteResponse of(Site s) {
            if (s == null) {
                return null;
            }
            return new SiteResponse(s.getId(), s.getSource(), s.getExternalId(), s.getName(), s.getAddress(),
                    s.getLocation() == null ? null : s.getLocation().getLatitude(),
                    s.getLocation() == null ? null : s.getLocation().getLongitude(),
                    s.getRadiusMeters());
        }
    }

    public record TaskResponse(Long id, String source, String externalId, String title, String description,
                               TaskStatus status, Instant dueAt, SiteResponse site) {
        public static TaskResponse of(Task t) {
            return new TaskResponse(t.getId(), t.getSource(), t.getExternalId(), t.getTitle(), t.getDescription(),
                    t.getStatus(), t.getDueAt(), SiteResponse.of(t.getSite()));
        }
    }
}
