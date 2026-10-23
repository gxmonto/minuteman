package com.baseline.timetrack.integration;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baseline.timetrack.config.AppProperties;
import com.baseline.timetrack.entity.ExternalAccount;
import com.baseline.timetrack.entity.GeoPoint;
import com.baseline.timetrack.entity.Site;
import com.baseline.timetrack.entity.Task;
import com.baseline.timetrack.entity.TaskStatus;
import com.baseline.timetrack.entity.User;
import com.baseline.timetrack.repository.ExternalAccountRepository;
import com.baseline.timetrack.repository.SiteRepository;
import com.baseline.timetrack.repository.TaskRepository;
import com.baseline.timetrack.repository.UserRepository;

/** Upserts provider-neutral tasks into the local model; shared by pull syncs and the inbound API. */
@Service
public class TaskImportService {

    private static final Logger log = LoggerFactory.getLogger(TaskImportService.class);

    private final TaskRepository tasks;
    private final SiteRepository sites;
    private final ExternalAccountRepository externalAccounts;
    private final UserRepository users;
    private final int defaultRadiusMeters;

    public TaskImportService(TaskRepository tasks, SiteRepository sites, ExternalAccountRepository externalAccounts,
                             UserRepository users, AppProperties props) {
        this.tasks = tasks;
        this.sites = sites;
        this.externalAccounts = externalAccounts;
        this.users = users;
        this.defaultRadiusMeters = props.sites().defaultRadiusMeters();
    }

    /** Pushed batch: assignees come from the payload. */
    @Transactional
    public int importBatch(String provider, Collection<ExternalTask> batch) {
        batch.forEach(t -> upsert(provider, t, null));
        return batch.size();
    }

    /**
     * Pulled list of one user's open tasks. Tasks of this provider that are no longer in the list
     * (closed, or reassigned in the external system) stop being assigned to the user.
     */
    @Transactional
    public int replaceOpenTasksForUser(String provider, Long userId, List<ExternalTask> openTasks) {
        User user = users.getReferenceById(userId);
        Set<Long> seen = new HashSet<>();
        int imported = 0;
        for (ExternalTask ext : openTasks) {
            if (!StringUtils.hasText(ext.externalId()) || !StringUtils.hasText(ext.title())) {
                log.warn("{}: skipping task without externalId/title: {}", provider, ext);
                continue;
            }
            seen.add(upsert(provider, ext, user).getId());
            imported++;
        }
        for (Task stale : tasks.findAssignedFromSource(userId, provider)) {
            if (!seen.contains(stale.getId())) {
                stale.getAssignees().removeIf(u -> u.getId().equals(userId));
            }
        }
        return imported;
    }

    private Task upsert(String provider, ExternalTask ext, User ensureAssignee) {
        Task task = tasks.findBySourceAndExternalId(provider, ext.externalId()).orElseGet(() -> {
            Task t = new Task();
            t.setSource(provider);
            t.setExternalId(ext.externalId());
            return t;
        });
        task.setTitle(ext.title());
        task.setDescription(ext.description());
        task.setStatus(ext.status() != null ? ext.status() : TaskStatus.OPEN);
        task.setDueAt(ext.dueAt());
        if (ext.site() != null) {
            task.setSite(upsertSite(provider, ext.site()));
        }
        if (ext.assigneeExternalUserIds() != null) {
            task.getAssignees().clear();
            task.getAssignees().addAll(resolveUsers(provider, ext.assigneeExternalUserIds()));
        }
        if (ensureAssignee != null && !task.isAssignedTo(ensureAssignee.getId())) {
            task.getAssignees().add(ensureAssignee);
        }
        return tasks.save(task);
    }

    private Site upsertSite(String provider, ExternalTask.ExternalSite ext) {
        Site site = sites.findBySourceAndExternalId(provider, ext.externalId()).orElseGet(() -> {
            Site s = new Site();
            s.setSource(provider);
            s.setExternalId(ext.externalId());
            s.setRadiusMeters(defaultRadiusMeters);
            return s;
        });
        site.setName(StringUtils.hasText(ext.name()) ? ext.name() : ext.externalId());
        if (ext.address() != null) {
            site.setAddress(ext.address());
        }
        if (!site.isLocationLocked()) {
            GeoPoint location = GeoPoint.of(ext.latitude(), ext.longitude());
            if (location != null) {
                site.setLocation(location);
            }
            if (ext.radiusMeters() != null && ext.radiusMeters() > 0) {
                site.setRadiusMeters(ext.radiusMeters());
            }
        }
        return sites.save(site);
    }

    private Set<User> resolveUsers(String provider, List<String> externalUserIds) {
        Set<User> resolved = externalAccounts.findByProviderAndExternalUserIdIn(provider, externalUserIds).stream()
                .map(ExternalAccount::getUser)
                .collect(Collectors.toSet());
        if (resolved.size() < new HashSet<>(externalUserIds).size()) {
            log.debug("{}: some assignees are not linked to a Minuteman user: {}", provider, externalUserIds);
        }
        return resolved;
    }
}
