package com.baseline.timetrack.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;
@Getter
@Setter

public class CurrentStatusResponse {

    private boolean workDayOpen;
    private LocalDateTime clockIn;

    private boolean timerRunning;
    private String timerType;
    private LocalDateTime timerStartedAt;

    private long secondsRunning;
    private long totalSecondsToday;

    // getters & setters
}
