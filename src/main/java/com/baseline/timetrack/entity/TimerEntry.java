package com.baseline.timetrack.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "timer_entries")
public class TimerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "work_day_id")
    private WorkDay workDay;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id")
    private Site site;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimerType type;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(length = 2000)
    private String notes;

    /** Same trick as {@link WorkDay#getOpenFlag()}: at most one running timer per user. */
    @Column(name = "open_flag")
    private Boolean openFlag = Boolean.TRUE;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "start_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "start_longitude"))
    })
    private GeoPoint startLocation;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "end_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "end_longitude"))
    })
    private GeoPoint endLocation;

    /** First time the technician was seen outside the site's geofence while this timer ran. */
    @Column(name = "left_site_at")
    private Instant leftSiteAt;

    @Column(name = "left_site_notified_at")
    private Instant leftSiteNotifiedAt;

    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private SyncStatus syncStatus = SyncStatus.NOT_REQUIRED;

    @Column(name = "sync_attempts", nullable = false)
    private int syncAttempts;

    @Column(name = "next_sync_at")
    private Instant nextSyncAt;

    @Column(name = "last_sync_error", length = 1000)
    private String lastSyncError;

    /** Id the external system gave this entry once pushed. */
    @Column(name = "external_id")
    private String externalId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isRunning() {
        return endTime == null;
    }

    public void stop(Instant at, GeoPoint location) {
        endTime = at;
        endLocation = location;
        durationSeconds = Duration.between(startTime, at).getSeconds();
        openFlag = null;
    }

    public long elapsedSeconds(Instant now) {
        return isRunning() ? Duration.between(startTime, now).getSeconds() : durationSeconds;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
