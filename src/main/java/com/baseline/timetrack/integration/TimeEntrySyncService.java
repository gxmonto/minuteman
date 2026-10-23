package com.baseline.timetrack.integration;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baseline.timetrack.common.ApiException;
import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.entity.ExternalAccount;
import com.baseline.timetrack.entity.SyncStatus;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.repository.ExternalAccountRepository;
import com.baseline.timetrack.repository.TimerEntryRepository;

/**
 * Outbox for closed time entries: stopping a timer only marks it PENDING in the same transaction,
 * and this job pushes it afterwards, retrying with exponential backoff while the external system is down.
 */
@Service
public class TimeEntrySyncService {

    private static final Logger log = LoggerFactory.getLogger(TimeEntrySyncService.class);
    private static final Duration MAX_BACKOFF = Duration.ofHours(6);

    private final TimerEntryRepository timers;
    private final ExternalAccountRepository externalAccounts;
    private final ConnectorRegistry registry;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int maxAttempts;

    public TimeEntrySyncService(TimerEntryRepository timers, ExternalAccountRepository externalAccounts,
                                ConnectorRegistry registry, TransactionTemplate tx, Clock clock, AppProperties props) {
        this.timers = timers;
        this.externalAccounts = externalAccounts;
        this.registry = registry;
        this.tx = tx;
        this.clock = clock;
        this.maxAttempts = props.integrations().maxPushAttempts();
    }

    /** Called inside the transaction that stops the timer. */
    public void markClosed(TimerEntry timer) {
        if (timer.getTask() != null && registry.canPushTimeEntries(timer.getTask().getSource())) {
            timer.setSyncStatus(SyncStatus.PENDING);
            timer.setNextSyncAt(clock.instant());
        } else {
            timer.setSyncStatus(SyncStatus.NOT_REQUIRED);
        }
    }

    @Scheduled(fixedDelayString = "${timetrack.integrations.time-entry-push-interval:PT1M}", initialDelayString = "PT30S")
    public void pushDue() {
        List<Long> due = timers.findIdsDueForSync(clock.instant(), PageRequest.of(0, 50));
        due.forEach(this::pushOne);
    }

    private record Prepared(IntegrationConnector connector, TimeEntryExport export) {
    }

    public void pushOne(Long timerId) {
        Prepared prepared;
        try {
            prepared = tx.execute(s -> prepare(timerId));
        } catch (RuntimeException e) {
            recordFailure(timerId, e);
            return;
        }
        if (prepared == null) {
            return;
        }
        try {
            Optional<String> externalId = prepared.connector().pushTimeEntry(prepared.export());
            tx.executeWithoutResult(s -> timers.findById(timerId).ifPresent(t -> {
                t.setSyncStatus(SyncStatus.SYNCED);
                t.setExternalId(externalId.orElse(null));
                t.setLastSyncError(null);
                t.setNextSyncAt(null);
            }));
        } catch (RuntimeException e) {
            recordFailure(timerId, e);
        }
    }

    private Prepared prepare(Long timerId) {
        TimerEntry timer = timers.findById(timerId).orElse(null);
        if (timer == null || timer.getSyncStatus() != SyncStatus.PENDING || timer.getTask() == null) {
            return null;
        }
        String provider = timer.getTask().getSource();
        IntegrationConnector connector = registry.find(provider)
                .filter(IntegrationConnector::supportsTimeEntryPush)
                .orElseThrow(() -> new ConnectorException("No connector accepts time entries for " + provider));
        String userExternalId = externalAccounts.findByUserIdAndProvider(timer.getUser().getId(), provider)
                .map(ExternalAccount::getExternalUserId)
                .orElseThrow(() -> new ConnectorException("User " + timer.getUser().getId() + " is not linked to " + provider));
        return new Prepared(connector, TimeEntryExport.of(timer, userExternalId));
    }

    private void recordFailure(Long timerId, RuntimeException error) {
        log.warn("Pushing time entry {} failed: {}", timerId, error.getMessage());
        tx.executeWithoutResult(s -> timers.findById(timerId).ifPresent(t -> {
            int attempts = t.getSyncAttempts() + 1;
            t.setSyncAttempts(attempts);
            t.setLastSyncError(truncate(error.getMessage()));
            if (attempts >= maxAttempts) {
                t.setSyncStatus(SyncStatus.FAILED);
                t.setNextSyncAt(null);
            } else {
                t.setNextSyncAt(clock.instant().plus(backoff(attempts)));
            }
        }));
    }

    static Duration backoff(int attempts) {
        Duration delay = Duration.ofMinutes(1L << Math.min(attempts, 20));
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    /** Admin action after fixing the cause (e.g. linking the user's external account). */
    public void retry(Long timerId) {
        tx.executeWithoutResult(s -> {
            TimerEntry t = timers.findById(timerId)
                    .orElseThrow(() -> ApiException.notFound("TIMER_NOT_FOUND", "Timer entry not found"));
            if (t.getSyncStatus() != SyncStatus.FAILED && t.getSyncStatus() != SyncStatus.PENDING) {
                throw ApiException.conflict("NOT_RETRYABLE", "Entry is " + t.getSyncStatus());
            }
            t.setSyncStatus(SyncStatus.PENDING);
            t.setSyncAttempts(0);
            t.setNextSyncAt(clock.instant());
        });
        pushOne(timerId);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
