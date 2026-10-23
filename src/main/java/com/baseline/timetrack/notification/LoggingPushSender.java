package com.baseline.timetrack.notification;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when FCM is not configured (local development): notifications only go to the log. */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        log.info("[push disabled] to {}: {} - {} {}", abbreviate(deviceToken), title, body, data);
        return Result.SENT;
    }

    private static String abbreviate(String token) {
        return token.length() <= 8 ? token : token.substring(0, 8) + "...";
    }
}
