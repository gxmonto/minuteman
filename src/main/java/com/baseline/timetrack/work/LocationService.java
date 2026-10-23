package com.baseline.timetrack.work;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.entity.GeoPoint;
import com.baseline.timetrack.entity.Site;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.entity.TimerType;
import com.baseline.timetrack.notification.NotificationService;
import com.baseline.timetrack.repository.TimerEntryRepository;
import com.baseline.timetrack.repository.UserRepository;
import com.baseline.timetrack.work.WorkDtos.LocationInput;
import com.baseline.timetrack.work.WorkDtos.LocationReportResponse;

/**
 * Receives location updates from the app (on a geofence exit event, or periodically while a timer runs)
 * and sends the "you left the site, stop the timer?" notification when the technician is outside the
 * geofence of the task they are timing.
 */
@Service
public class LocationService {

    private final TimerEntryRepository timers;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AppProperties.Reminders props;
    private final Clock clock;

    public LocationService(TimerEntryRepository timers, UserRepository users, NotificationService notifications,
                           AppProperties props, Clock clock) {
        this.timers = timers;
        this.users = users;
        this.notifications = notifications;
        this.props = props.reminders();
        this.clock = clock;
    }

    @Transactional
    public LocationReportResponse report(Long userId, LocationInput input) {
        users.lockById(userId);
        TimerEntry timer = timers.findRunning(userId).orElse(null);
        if (timer == null) {
            return new LocationReportResponse(false, null, null, null, false);
        }
        Site site = timer.getSite();
        if (timer.getType() != TimerType.TASK || site == null || site.getLocation() == null) {
            return new LocationReportResponse(true, site == null ? null : site.getId(), null, null, false);
        }

        GeoPoint here = input.toGeoPoint();
        double distance = site.getLocation().distanceTo(here);
        double accuracy = input.accuracyMeters() == null ? 0 : input.accuracyMeters();
        double allowed = site.getRadiusMeters() + Math.max(props.leftSiteToleranceMeters(), accuracy);
        boolean inside = distance <= allowed;

        Instant now = clock.instant();
        boolean notified = false;
        if (inside) {
            // Back on site: re-arm so the next departure notifies again.
            timer.setLeftSiteAt(null);
            timer.setLeftSiteNotifiedAt(null);
        } else {
            if (timer.getLeftSiteAt() == null) {
                timer.setLeftSiteAt(now);
            }
            Instant lastNotice = timer.getLeftSiteNotifiedAt();
            if (lastNotice == null || !lastNotice.isAfter(now.minus(props.leftSiteRepeatEvery()))) {
                notifications.notify(userId, "notification.left-site",
                        new Object[] {site.getName(), timer.getTask().getTitle()},
                        Map.of("type", "LEFT_SITE", "timerId", timer.getId().toString(),
                                "siteId", site.getId().toString()));
                timer.setLeftSiteNotifiedAt(now);
                notified = true;
            }
        }
        return new LocationReportResponse(true, site.getId(), Math.round(distance * 10) / 10.0, inside, notified);
    }
}
