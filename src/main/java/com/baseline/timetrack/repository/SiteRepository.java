package com.baseline.timetrack.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.baseline.timetrack.entity.Site;

public interface SiteRepository extends JpaRepository<Site, Long> {

    Optional<Site> findBySourceAndExternalId(String source, String externalId);
}
