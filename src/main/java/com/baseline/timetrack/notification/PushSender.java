package com.baseline.timetrack.notification;

import java.util.Map;

public interface PushSender {

    enum Result {
        SENT,
        /** The token is no longer valid (app uninstalled, token rotated) and should be forgotten. */
        INVALID_TOKEN,
        FAILED
    }

    Result send(String deviceToken, String title, String body, Map<String, String> data);
}
