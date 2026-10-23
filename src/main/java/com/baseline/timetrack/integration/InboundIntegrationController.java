package com.baseline.timetrack.integration;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baseline.timetrack.common.ApiException;
import com.baseline.timetrack.entity.ExternalAccount;
import com.baseline.timetrack.entity.TimerEntry;
import com.baseline.timetrack.repository.ExternalAccountRepository;
import com.baseline.timetrack.repository.TimerEntryRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * API for systems that integrate by themselves instead of through a connector:
 * they push their tasks and page through closed time entries with a cursor.
 * Authenticated with {@code X-Api-Key}; each key only grants access to its own provider.
 */
@RestController
@RequestMapping("/api/integrations/inbound/{provider}")
public class InboundIntegrationController {

    private final TaskImportService importService;
    private final TimerEntryRepository timers;
    private final ExternalAccountRepository externalAccounts;

    public InboundIntegrationController(TaskImportService importService, TimerEntryRepository timers,
                                        ExternalAccountRepository externalAccounts) {
        this.importService = importService;
        this.timers = timers;
        this.externalAccounts = externalAccounts;
    }

    public record TaskBatch(@NotNull @Size(max = 500) List<@Valid @NotNull ExternalTask> tasks) {
    }

    public record ImportResult(int imported) {
    }

    public record TimeEntryPage(List<TimeEntryExport> items, String nextCursor) {
    }

    @PutMapping("/tasks")
    public ImportResult putTasks(@PathVariable String provider, @Valid @RequestBody TaskBatch batch, Authentication auth) {
        requireOwnProvider(provider, auth);
        return new ImportResult(importService.importBatch(provider, batch.tasks()));
    }

    /**
     * Closed time entries on this provider's tasks, oldest change first. Pass {@code nextCursor} back as
     * {@code cursor} to continue; entries can show up again after a change, so de-duplicate by {@code id}.
     */
    @GetMapping("/time-entries")
    @Transactional(readOnly = true)
    public TimeEntryPage timeEntries(@PathVariable String provider,
                                     @RequestParam(required = false) String cursor,
                                     @RequestParam(defaultValue = "100") int limit,
                                     Authentication auth) {
        requireOwnProvider(provider, auth);
        Cursor from = Cursor.decode(cursor);
        List<TimerEntry> page = timers.findClosedForSourceAfter(provider, from.updatedAt(), from.id(),
                PageRequest.of(0, Math.clamp(limit, 1, 500)));
        Map<Long, String> externalUserIds = externalAccounts.findByProviderAndUserIdIn(provider,
                        page.stream().map(t -> t.getUser().getId()).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), ExternalAccount::getExternalUserId));
        List<TimeEntryExport> items = page.stream()
                .map(t -> TimeEntryExport.of(t, externalUserIds.get(t.getUser().getId())))
                .toList();
        String next = page.isEmpty() ? cursor
                : new Cursor(page.getLast().getUpdatedAt(), page.getLast().getId()).encode();
        return new TimeEntryPage(items, next);
    }

    private static void requireOwnProvider(String provider, Authentication auth) {
        if (!provider.equals(auth.getName())) {
            throw ApiException.forbidden("WRONG_PROVIDER", "This API key is not valid for provider " + provider);
        }
    }

    record Cursor(Instant updatedAt, Long id) {

        static Cursor decode(String value) {
            if (value == null || value.isBlank()) {
                return new Cursor(Instant.EPOCH, 0L);
            }
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8).split("\\|");
                return new Cursor(Instant.parse(parts[0]), Long.valueOf(parts[1]));
            } catch (RuntimeException e) {
                throw ApiException.badRequest("INVALID_CURSOR", "Invalid cursor");
            }
        }

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString((updatedAt + "|" + id).getBytes(StandardCharsets.UTF_8));
        }
    }
}
