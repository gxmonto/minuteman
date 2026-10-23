package com.baseline.timetrack.entity;

/**
 * Outbound sync state of a closed timer entry towards the system that owns its task.
 */
public enum SyncStatus {
    /** Timer still running, or nothing to push (local task, travel, or a provider that pulls entries itself). */
    NOT_REQUIRED,
    PENDING,
    SYNCED,
    /** Gave up after the maximum number of attempts; an admin can retry it. */
    FAILED
}
