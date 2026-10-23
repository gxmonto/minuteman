package com.baseline.timetrack.work;

import java.time.Instant;

import com.baseline.timetrack.entity.GeoPoint;
import com.baseline.timetrack.entity.SyncStatus;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.entity.TimerType;
import com.baseline.timetrack.entity.WorkDay;
import com.baseline.timetrack.task.TaskDtos.SiteResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public final class WorkDtos {

    private WorkDtos() {
    }

    public record LocationInput(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            /** Reported GPS accuracy; widens the geofence tolerance when the fix is poor. */
            @PositiveOrZero Double accuracyMeters) {

        public GeoPoint toGeoPoint() {
            return GeoPoint.of(latitude, longitude);
        }

        static GeoPoint geoPointOf(LocationInput input) {
            return input == null ? null : input.toGeoPoint();
        }
    }

    public record ClockRequest(@Valid LocationInput location) {
    }

    public record StartTimerRequest(
            @NotNull TimerType type,
            /** Required for TASK; optional destination for TRAVEL. */
            Long taskId,
            @Size(max = 2000) String notes,
            @Valid LocationInput location) {
    }

    public record StopTimerRequest(@Size(max = 2000) String notes, @Valid LocationInput location) {
    }

    public record WorkDayResponse(Long id, Instant clockIn, Instant clockOut, WorkDay.Status status) {
        static WorkDayResponse of(WorkDay d) {
            return new WorkDayResponse(d.getId(), d.getClockIn(), d.getClockOut(), d.getStatus());
        }
    }

    public record TimerResponse(
            Long id,
            TimerType type,
            Long taskId,
            String taskTitle,
            Long siteId,
            /** Site with its geofence, so the app can watch it while the timer runs. */
            SiteResponse site,
            Instant startTime,
            Instant endTime,
            Long durationSeconds,
            String notes,
            Instant leftSiteAt,
            SyncStatus syncStatus) {

        static TimerResponse of(TimerEntry t) {
            return new TimerResponse(
                    t.getId(),
                    t.getType(),
                    t.getTask() == null ? null : t.getTask().getId(),
                    t.getTask() == null ? null : t.getTask().getTitle(),
                    t.getSite() == null ? null : t.getSite().getId(),
                    SiteResponse.of(t.getSite()),
                    t.getStartTime(),
                    t.getEndTime(),
                    t.getDurationSeconds(),
                    t.getNotes(),
                    t.getLeftSiteAt(),
                    t.getSyncStatus());
        }
    }

    /**
     * @param totalSecondsToday closed timers of the open work day plus the running one
     * @param serverTime lets the app correct for a wrong phone clock when it ticks the timer locally
     */
    public record CurrentStatusResponse(
            boolean workDayOpen,
            WorkDayResponse workDay,
            TimerResponse timer,
            long secondsRunning,
            long totalSecondsToday,
            Instant serverTime) {
    }

    /**
     * @param insideSite {@code null} when there is nothing to compare against (no task timer, or the site has no geofence)
     */
    public record LocationReportResponse(
            boolean timerRunning,
            Long siteId,
            Double distanceMeters,
            Boolean insideSite,
            boolean notified) {
    }
}
