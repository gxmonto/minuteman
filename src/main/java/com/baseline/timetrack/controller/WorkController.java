package com.baseline.timetrack.controller;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baseline.timetrack.dto.CurrentStatusResponse;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.entity.WorkDay;
import com.baseline.timetrack.repository.TimerEntryRepository;
import com.baseline.timetrack.repository.UserRepository;
import com.baseline.timetrack.repository.WorkDayRepository;

@RestController
@RequestMapping("/api/work")
public class WorkController {

    private final WorkDayRepository workDayRepo;
    private final TimerEntryRepository timerRepo;
    private final UserRepository userRepo;

    public WorkController(
            WorkDayRepository workDayRepo,
            TimerEntryRepository timerRepo,
            UserRepository userRepo) {
        this.workDayRepo = workDayRepo;
        this.timerRepo = timerRepo;
        this.userRepo = userRepo;
    }

    // ▶️ START DAY
    @PostMapping("/start-day/{userId}")
    public WorkDay startDay(@PathVariable Long userId) {

        userRepo.findById(userId).orElseThrow();

        workDayRepo.findByUserIdAndStatus(userId, WorkDay.Status.OPEN)
                .ifPresent(wd -> {
                    throw new RuntimeException("Work day already open");
                });

        WorkDay wd = new WorkDay();
        wd.setUser(userRepo.getReferenceById(userId));
        wd.setClockIn(LocalDateTime.now());
        wd.setStatus(WorkDay.Status.OPEN);

        return workDayRepo.save(wd);
    }

    // ▶️ END DAY
    @PostMapping("/end-day/{userId}")
    public WorkDay endDay(@PathVariable Long userId) {

        WorkDay wd = workDayRepo.findByUserIdAndStatus(userId, WorkDay.Status.OPEN)
                .orElseThrow(() -> new RuntimeException("No open work day"));

        // close open timer if exists
        timerRepo.findByUserIdAndEndTimeIsNull(userId).ifPresent(timer -> {
            timer.setEndTime(LocalDateTime.now());
            timer.setDurationSeconds(
                    (int) Duration.between(timer.getStartTime(), timer.getEndTime()).getSeconds()
            );
            timerRepo.save(timer);
        });

        wd.setClockOut(LocalDateTime.now());
        wd.setStatus(WorkDay.Status.CLOSED);

        return workDayRepo.save(wd);
    }

    // ▶️ START TIMER
    @PostMapping("/start-timer/{userId}")
    public TimerEntry startTimer(
            @PathVariable Long userId,
            @RequestParam TimerEntry.Type type) {

        WorkDay wd = workDayRepo.findByUserIdAndStatus(userId, WorkDay.Status.OPEN)
                .orElseThrow(() -> new RuntimeException("No open work day"));

        timerRepo.findByUserIdAndEndTimeIsNull(userId)
                .ifPresent(t -> {
                    throw new RuntimeException("Timer already running");
                });

        TimerEntry timer = new TimerEntry();
        timer.setUser(userRepo.getReferenceById(userId));
        timer.setWorkDay(wd);
        timer.setType(type);
        timer.setStartTime(LocalDateTime.now());

        return timerRepo.save(timer);
    }

    // ▶️ STOP TIMER
    @PostMapping("/stop-timer/{userId}")
    public TimerEntry stopTimer(@PathVariable Long userId) {

        TimerEntry timer = timerRepo.findByUserIdAndEndTimeIsNull(userId)
                .orElseThrow(() -> new RuntimeException("No running timer"));

        timer.setEndTime(LocalDateTime.now());
        timer.setDurationSeconds(
                (int) Duration.between(timer.getStartTime(), timer.getEndTime()).getSeconds()
        );

        return timerRepo.save(timer);
    }

@GetMapping("/current/{userId}")
public CurrentStatusResponse getCurrentStatus(@PathVariable Long userId) {

    CurrentStatusResponse response = new CurrentStatusResponse();

    WorkDay workDay = workDayRepo
            .findByUserIdAndStatus(userId, WorkDay.Status.OPEN)
            .orElse(null);

    if (workDay == null) {
        response.setWorkDayOpen(false);
        return response;
    }

    response.setWorkDayOpen(true);
    response.setClockIn(workDay.getClockIn());

    TimerEntry runningTimer = timerRepo
            .findByUserIdAndEndTimeIsNull(userId)
            .orElse(null);

    if (runningTimer != null) {
        response.setTimerRunning(true);
        response.setTimerType(runningTimer.getType().name());
        response.setTimerStartedAt(runningTimer.getStartTime());

        long secondsRunning =
                java.time.Duration
                        .between(runningTimer.getStartTime(), java.time.LocalDateTime.now())
                        .getSeconds();

        response.setSecondsRunning(secondsRunning);
    } else {
        response.setTimerRunning(false);
    }

    // total seconds today (closed timers only)
    long totalSeconds = workDay.getId() == null ? 0 :
            timerRepo.findAll().stream()
                    .filter(t -> t.getWorkDay().getId().equals(workDay.getId()))
                    .filter(t -> t.getDurationSeconds() != null)
                    .mapToLong(TimerEntry::getDurationSeconds)
                    .sum();

    response.setTotalSecondsToday(totalSeconds);

    return response;
}


}
