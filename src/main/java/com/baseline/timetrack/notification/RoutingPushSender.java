package com.baseline.timetrack.notification;

import java.util.Map;

/** Sends Expo tokens (from the mobile app) through Expo and anything else through the native sender. */
public class RoutingPushSender implements PushSender {

    private final PushSender expo;
    private final PushSender fallback;

    public RoutingPushSender(PushSender expo, PushSender fallback) {
        this.expo = expo;
        this.fallback = fallback;
    }

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        return (ExpoPushSender.handles(deviceToken) ? expo : fallback).send(deviceToken, title, body, data);
    }
}
