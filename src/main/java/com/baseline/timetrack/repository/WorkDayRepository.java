package com.baseline.timetrack.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baseline.timetrack.entity.WorkDay;

public interface WorkDayRepository extends JpaRepository<WorkDay, Long> {

    Optional<WorkDay> findByUserIdAndStatus(Long userId, WorkDay.Status status);
}
