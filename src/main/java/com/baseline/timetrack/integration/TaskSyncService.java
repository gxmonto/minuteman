package com.baseline.timetrack.integration;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.baseline.timetrack.common.ApiException;
import com.baseline.timetrack.entity.ExternalAccount;
import com.baseline.timetrack.repository.ExternalAccountRepository;

/** Pulls open tasks from every connector for every linked user. HTTP calls run outside DB transactions. */
@Service
public class TaskSyncService {

    private static final Logger log = LoggerFactory.getLogger(TaskSyncService.class);

    private final ConnectorRegistry registry;
    private final ExternalAccountRepository externalAccounts;
    private final TaskImportService importService;
    private final TransactionTemplate tx;

    public TaskSyncService(ConnectorRegistry registry, ExternalAccountRepository externalAccounts,
                           TaskImportService importService, TransactionTemplate tx) {
        this.registry = registry;
        this.externalAccounts = externalAccounts;
        this.importService = importService;
        this.tx = tx;
    }

    public record SyncResult(String provider, int usersSynced, int tasksImported, List<String> errors) {
    }

    @Scheduled(fixedDelayString = "${timetrack.integrations.task-sync-interval:PT10M}", initialDelayString = "PT1M")
    public void syncAll() {
        for (IntegrationConnector connector : registry.all()) {
            SyncResult result = sync(connector, externalAccounts.findByProvider(connector.provider()));
            log.info("Task sync {}: {} users, {} tasks, {} errors",
                    result.provider(), result.usersSynced(), result.tasksImported(), result.errors().size());
        }
    }

    public SyncResult syncProvider(String provider) {
        IntegrationConnector connector = registry.find(provider)
                .orElseThrow(() -> ApiException.notFound("UNKNOWN_PROVIDER", "No connector configured for " + provider));
        return sync(connector, externalAccounts.findByProvider(provider));
    }

    /** Refreshes only one user's tasks, e.g. on pull-to-refresh in the app. */
    public List<SyncResult> syncUser(Long userId) {
        List<SyncResult> results = new ArrayList<>();
        for (ExternalAccount account : externalAccounts.findByUserId(userId)) {
            registry.find(account.getProvider()).ifPresent(c -> results.add(sync(c, List.of(account))));
        }
        return results;
    }

    private SyncResult sync(IntegrationConnector connector, List<ExternalAccount> accounts) {
        int users = 0;
        int imported = 0;
        List<String> errors = new ArrayList<>();
        for (ExternalAccount account : accounts) {
            Long userId = account.getUser().getId();
            try {
                List<ExternalTask> open = connector.fetchOpenTasks(account.getExternalUserId());
                Integer count = tx.execute(s -> importService.replaceOpenTasksForUser(connector.provider(), userId, open));
                imported += count == null ? 0 : count;
                users++;
            } catch (RuntimeException e) {
                log.warn("Task sync {} failed for user {}: {}", connector.provider(), userId, e.getMessage());
                errors.add("user " + userId + ": " + e.getMessage());
            }
        }
        return new SyncResult(connector.provider(), users, imported, errors);
    }
}
