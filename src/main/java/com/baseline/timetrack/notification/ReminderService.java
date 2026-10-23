package com.baseline.timetrack.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.entity.WorkDay;
import com.baseline.timetrack.repository.TimerEntryRepository;
import com.baseline.timetrack.repository.WorkDayRepository;

/**
 * Server-side safety net: reminds about timers and work days left open too long, even when the phone
 * never reported a location (GPS off, app killed).
 */
@Service
public class ReminderService {

    private final TimerEntryRepository timers;
    private final WorkDayRepository workDays;
    private final NotificationService notifications;
    private final AppProperties.Reminders props;
    private final Clock clock;

    public ReminderService(TimerEntryRepository timers, WorkDayRepository workDays, NotificationService notifications,
                           AppProperties props, Clock clock) {
        this.timers = timers;
        this.workDays = workDays;
        this.notifications = notifications;
        this.props = props.reminders();
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${timetrack.reminders.scan-interval:PT5M}", initialDelayString = "PT1M")
    @Transactional
    public void scan() {
        Instant now = clock.instant();
        Instant remindedBefore = now.minus(props.repeatEvery());

        for (TimerEntry timer : timers.findNeedingReminder(now.minus(props.timerOpenAfter()), remindedBefore)) {
            String what = timer.getTask() != null ? timer.getTask().getTitle() : timer.getType().name();
            notifications.notify(timer.getUser().getId(), "notification.timer-open",
                    new Object[] {what, hours(timer.getStartTime(), now)},
                    Map.of("type", "TIMER_OPEN", "timerId", timer.getId().toString()));
            timer.setLastReminderAt(now);
        }

        for (WorkDay day : workDays.findNeedingReminder(now.minus(props.workDayOpenAfter()), remindedBefore)) {
            notifications.notify(day.getUser().getId(), "notification.workday-open",
                    new Object[] {hours(day.getClockIn(), now)},
                    Map.of("type", "WORKDAY_OPEN", "workDayId", day.getId().toString()));
            day.setLastReminderAt(now);
        }
    }

    private static long hours(Instant from, Instant to) {
        return Duration.between(from, to).toHours();
    }
}
