package com.baseline.timetrack.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baseline.timetrack.entity.TimerEntry;

public interface TimerEntryRepository extends JpaRepository<TimerEntry, Long> {

    Optional<TimerEntry> findByUserIdAndEndTimeIsNull(Long userId);
}
