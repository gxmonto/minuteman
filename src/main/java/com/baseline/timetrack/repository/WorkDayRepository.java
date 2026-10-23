package com.baseline.timetrack.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.baseline.timetrack.entity.WorkDay;

public interface WorkDayRepository extends JpaRepository<WorkDay, Long> {

    Optional<WorkDay> findByUserIdAndStatus(Long userId, WorkDay.Status status);

    default Optional<WorkDay> findOpen(Long userId) {
        return findByUserIdAndStatus(userId, WorkDay.Status.OPEN);
    }

    @Query("""
            select w from WorkDay w join fetch w.user
            where w.status = com.baseline.timetrack.entity.WorkDay.Status.OPEN
              and w.clockIn < :openSince
              and (w.lastReminderAt is null or w.lastReminderAt < :remindedBefore)""")
    List<WorkDay> findNeedingReminder(@Param("openSince") Instant openSince,
                                      @Param("remindedBefore") Instant remindedBefore);
}
