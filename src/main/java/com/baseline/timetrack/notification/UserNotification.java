package com.baseline.timetrack.notification;

import java.util.Map;

/**
 * Published inside a transaction and delivered only after it commits, so a rolled-back change never
 * notifies anyone and push latency never holds DB locks.
 *
 * @param data machine-readable payload for the app, e.g. {@code type=LEFT_SITE, timerId=42}
 */
public record UserNotification(Long userId, String title, String body, Map<String, String> data) {
}
