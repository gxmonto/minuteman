package com.baseline.timetrack.support;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.baseline.timetrack.integration.ConnectorException;
import com.baseline.timetrack.integration.ExternalTask;
import com.baseline.timetrack.integration.IntegrationConnector;
import com.baseline.timetrack.integration.TimeEntryExport;

public class FakeConnector implements IntegrationConnector {

    public final Map<String, List<ExternalTask>> tasksByUser = new ConcurrentHashMap<>();
    public final List<TimeEntryExport> pushed = new CopyOnWriteArrayList<>();
    public volatile boolean failPushes;

    @Override
    public String provider() {
        return "fakecrm";
    }

    @Override
    public List<ExternalTask> fetchOpenTasks(String externalUserId) {
        return tasksByUser.getOrDefault(externalUserId, List.of());
    }

    @Override
    public boolean supportsTimeEntryPush() {
        return true;
    }

    @Override
    public Optional<String> pushTimeEntry(TimeEntryExport entry) {
        if (failPushes) {
            throw new ConnectorException("fakecrm is down");
        }
        pushed.add(entry);
        return Optional.of("ext-" + entry.id());
    }
}
