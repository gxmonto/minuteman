package com.baseline.timetrack.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baseline.timetrack.entity.SyncStatus;
import com.baseline.timetrack.entity.TimerEntry;

public interface TimerEntryRepository extends JpaRepository<TimerEntry, Long> {

    @EntityGraph(attributePaths = {"task", "site"})
    Optional<TimerEntry> findByUserIdAndEndTimeIsNull(Long userId);

    default Optional<TimerEntry> findRunning(Long userId) {
        return findByUserIdAndEndTimeIsNull(userId);
    }

    @Query("""
            select coalesce(sum(t.durationSeconds), 0) from TimerEntry t
            where t.workDay.id = :workDayId and t.endTime is not null""")
    long sumClosedSeconds(@Param("workDayId") Long workDayId);

    @Query("""
            select t from TimerEntry t join fetch t.user left join fetch t.task
            where t.endTime is null
              and t.startTime < :startedBefore
              and (t.lastReminderAt is null or t.lastReminderAt < :remindedBefore)""")
    List<TimerEntry> findNeedingReminder(@Param("startedBefore") Instant startedBefore,
                                         @Param("remindedBefore") Instant remindedBefore);

    @Query("""
            select t.id from TimerEntry t
            where t.syncStatus = com.baseline.timetrack.entity.SyncStatus.PENDING
              and (t.nextSyncAt is null or t.nextSyncAt <= :now)
            order by t.nextSyncAt asc, t.id asc""")
    List<Long> findIdsDueForSync(@Param("now") Instant now, Pageable page);

    @EntityGraph(attributePaths = {"task", "user"})
    List<TimerEntry> findBySyncStatusOrderByIdDesc(SyncStatus status, Pageable page);

    /** Closed entries on tasks of a provider, in a stable (updatedAt, id) order for cursor paging. */
    @Query("""
            select t from TimerEntry t join fetch t.task task join fetch t.user
            where task.source = :source and t.endTime is not null
              and (t.updatedAt > :since or (t.updatedAt = :since and t.id > :afterId))
            order by t.updatedAt asc, t.id asc""")
    List<TimerEntry> findClosedForSourceAfter(@Param("source") String source,
                                              @Param("since") Instant since,
                                              @Param("afterId") Long afterId,
                                              Pageable page);
}
