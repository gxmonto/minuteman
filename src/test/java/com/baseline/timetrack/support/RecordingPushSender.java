package com.baseline.timetrack.support;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.baseline.timetrack.notification.PushSender;

public class RecordingPushSender implements PushSender {

    public record Sent(String token, String title, String body, Map<String, String> data) {
    }

    private final List<Sent> sent = new CopyOnWriteArrayList<>();

    @Override
    public Result send(String deviceToken, String title, String body, Map<String, String> data) {
        sent.add(new Sent(deviceToken, title, body, data));
        return Result.SENT;
    }

    public List<Sent> sentTo(String token) {
        return sent.stream().filter(s -> s.token().equals(token)).toList();
    }
}
