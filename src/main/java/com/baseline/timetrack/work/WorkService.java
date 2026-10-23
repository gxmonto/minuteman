package com.baseline.timetrack.work;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baseline.timetrack.common.ApiException;
import com.baseline.timetrack.entity.GeoPoint;
import com.baseline.timetrack.entity.Task;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.entity.TimerType;
import com.baseline.timetrack.entity.User;
import com.baseline.timetrack.entity.WorkDay;
import com.baseline.timetrack.integration.TimeEntrySyncService;
import com.baseline.timetrack.repository.TaskRepository;
import com.baseline.timetrack.repository.TimerEntryRepository;
import com.baseline.timetrack.repository.UserRepository;
import com.baseline.timetrack.repository.WorkDayRepository;
import com.baseline.timetrack.work.WorkDtos.CurrentStatusResponse;
import com.baseline.timetrack.work.WorkDtos.StartTimerRequest;
import com.baseline.timetrack.work.WorkDtos.TimerResponse;
import com.baseline.timetrack.work.WorkDtos.WorkDayResponse;

/**
 * Clock-in/out and timers. Every mutation first locks the user's row, so concurrent requests from
 * the same technician run one after another; the unique "open" indexes are the last line of defense.
 */
@Service
public class WorkService {

    private final UserRepository users;
    private final WorkDayRepository workDays;
    private final TimerEntryRepository timers;
    private final TaskRepository tasks;
    private final TimeEntrySyncService timeEntrySync;
    private final Clock clock;

    public WorkService(UserRepository users, WorkDayRepository workDays, TimerEntryRepository timers,
                       TaskRepository tasks, TimeEntrySyncService timeEntrySync, Clock clock) {
        this.users = users;
        this.workDays = workDays;
        this.timers = timers;
        this.tasks = tasks;
        this.timeEntrySync = timeEntrySync;
        this.clock = clock;
    }

    @Transactional
    public WorkDayResponse startDay(Long userId, GeoPoint location) {
        User user = lockUser(userId);
        if (workDays.findOpen(userId).isPresent()) {
            throw ApiException.conflict("WORKDAY_ALREADY_OPEN", "Work day already open");
        }
        WorkDay day = new WorkDay();
        day.setUser(user);
        day.setClockIn(clock.instant());
        day.setClockInLocation(location);
        return WorkDayResponse.of(workDays.saveAndFlush(day));
    }

    @Transactional
    public WorkDayResponse endDay(Long userId, GeoPoint location) {
        lockUser(userId);
        WorkDay day = requireOpenDay(userId);
        Instant now = clock.instant();
        timers.findRunning(userId).ifPresent(timer -> stop(timer, now, location, null));
        day.close(now, location);
        return WorkDayResponse.of(day);
    }

    @Transactional
    public TimerResponse startTimer(Long userId, StartTimerRequest request) {
        User user = lockUser(userId);
        WorkDay day = requireOpenDay(userId);
        if (timers.findRunning(userId).isPresent()) {
            throw ApiException.conflict("TIMER_ALREADY_RUNNING", "Stop the running timer first");
        }
        if (request.type() == TimerType.TASK && request.taskId() == null) {
            throw ApiException.badRequest("TASK_REQUIRED", "A TASK timer needs a taskId");
        }

        Task task = null;
        if (request.taskId() != null) {
            task = tasks.findById(request.taskId())
                    .orElseThrow(() -> ApiException.notFound("TASK_NOT_FOUND", "Task not found"));
            if (!task.isAssignedTo(userId)) {
                throw ApiException.forbidden("TASK_NOT_ASSIGNED", "This task is not assigned to you");
            }
            if (!task.getStatus().isWorkable()) {
                throw ApiException.conflict("TASK_CLOSED", "This task is " + task.getStatus());
            }
        }

        TimerEntry timer = new TimerEntry();
        timer.setUser(user);
        timer.setWorkDay(day);
        timer.setType(request.type());
        timer.setTask(task);
        timer.setSite(task == null ? null : task.getSite());
        timer.setNotes(request.notes());
        timer.setStartTime(clock.instant());
        timer.setStartLocation(WorkDtos.LocationInput.geoPointOf(request.location()));
        return TimerResponse.of(timers.saveAndFlush(timer));
    }

    @Transactional
    public TimerResponse stopTimer(Long userId, String notes, GeoPoint location) {
        lockUser(userId);
        TimerEntry timer = timers.findRunning(userId)
                .orElseThrow(() -> ApiException.conflict("NO_RUNNING_TIMER", "No timer is running"));
        stop(timer, clock.instant(), location, notes);
        return TimerResponse.of(timer);
    }

    @Transactional(readOnly = true)
    public CurrentStatusResponse current(Long userId) {
        Instant now = clock.instant();
        WorkDay day = workDays.findOpen(userId).orElse(null);
        TimerEntry running = timers.findRunning(userId).orElse(null);
        if (day == null) {
            return new CurrentStatusResponse(false, null, null, 0, 0, now);
        }
        long secondsRunning = running == null ? 0 : running.elapsedSeconds(now);
        long total = timers.sumClosedSeconds(day.getId()) + secondsRunning;
        return new CurrentStatusResponse(true, WorkDayResponse.of(day),
                running == null ? null : TimerResponse.of(running), secondsRunning, total, now);
    }

    private void stop(TimerEntry timer, Instant at, GeoPoint location, String notes) {
        timer.stop(at, location);
        if (notes != null) {
            timer.setNotes(notes);
        }
        timeEntrySync.markClosed(timer);
    }

    private User lockUser(Long userId) {
        return users.lockById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    private WorkDay requireOpenDay(Long userId) {
        return workDays.findOpen(userId)
                .orElseThrow(() -> ApiException.conflict("NO_OPEN_WORKDAY", "Clock in first"));
    }
}
