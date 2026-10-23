package com.baseline.timetrack.entity;

import java.time.Instant;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "sites")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Provider key the site came from, or {@code local}. */
    @Column(nullable = false)
    private String source;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private String name;

    private String address;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "latitude", column = @Column(name = "latitude")),
        @AttributeOverride(name = "longitude", column = @Column(name = "longitude"))
    })
    private GeoPoint location;

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    /** Set when an admin placed the geofence by hand, so provider syncs don't overwrite it. */
    @Column(name = "location_locked", nullable = false)
    private boolean locationLocked;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
