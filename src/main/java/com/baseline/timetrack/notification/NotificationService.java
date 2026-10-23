package com.baseline.timetrack.notification;

import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.entity.DeviceToken;
import com.baseline.timetrack.repository.DeviceTokenRepository;

@Service
public class NotificationService {

    private final ApplicationEventPublisher events;
    private final MessageSource messages;
    private final DeviceTokenRepository deviceTokens;
    private final PushSender pushSender;
    private final AppProperties props;

    public NotificationService(ApplicationEventPublisher events, MessageSource messages,
                               DeviceTokenRepository deviceTokens, PushSender pushSender, AppProperties props) {
        this.events = events;
        this.messages = messages;
        this.deviceTokens = deviceTokens;
        this.pushSender = pushSender;
        this.props = props;
    }

    /** Queues a notification whose texts come from messages.properties ({@code key.title}, {@code key.body}). */
    public void notify(Long userId, String messageKey, Object[] args, Map<String, String> data) {
        String title = messages.getMessage(messageKey + ".title", args, props.locale());
        String body = messages.getMessage(messageKey + ".body", args, props.locale());
        events.publishEvent(new UserNotification(userId, title, body, data));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UserNotification notification) {
        for (DeviceToken device : deviceTokens.findByUserId(notification.userId())) {
            PushSender.Result result = pushSender.send(device.getToken(), notification.title(), notification.body(),
                    notification.data());
            if (result == PushSender.Result.INVALID_TOKEN) {
                deviceTokens.delete(device);
            }
        }
    }
}
