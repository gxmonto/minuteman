package com.baseline.timetrack.entity;

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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "work_days")
public class WorkDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "clock_in", nullable = false)
    private Instant clockIn;

    @Column(name = "clock_out")
    private Instant clockOut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.OPEN;

    /**
     * TRUE while open, NULL once closed. Together with the unique (user_id, open_flag) index this lets
     * the database itself guarantee a single open work day per user.
     */
    @Column(name = "open_flag")
    private Boolean openFlag = Boolean.TRUE;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "clock_in_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "clock_in_longitude"))
    })
    private GeoPoint clockInLocation;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "clock_out_latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "clock_out_longitude"))
    })
    private GeoPoint clockOutLocation;

    @Column(name = "last_reminder_at")
    private Instant lastReminderAt;

    public enum Status {
        OPEN,
        CLOSED
    }

    public void close(Instant at, GeoPoint location) {
        clockOut = at;
        clockOutLocation = location;
        status = Status.CLOSED;
        openFlag = null;
    }
}
