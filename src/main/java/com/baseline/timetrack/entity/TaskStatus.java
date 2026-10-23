package com.baseline.timetrack.entity;

public enum TaskStatus {
    OPEN,
    IN_PROGRESS,
    DONE,
    CANCELLED;

    public boolean isWorkable() {
        return this == OPEN || this == IN_PROGRESS;
    }
}
