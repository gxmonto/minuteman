package com.baseline.timetrack.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.baseline.timetrack.entity.ExternalAccount;

public interface ExternalAccountRepository extends JpaRepository<ExternalAccount, Long> {

    @EntityGraph(attributePaths = "user")
    List<ExternalAccount> findByProvider(String provider);

    @EntityGraph(attributePaths = "user")
    List<ExternalAccount> findByProviderAndExternalUserIdIn(String provider, Collection<String> externalUserIds);

    @EntityGraph(attributePaths = "user")
    List<ExternalAccount> findByProviderAndUserIdIn(String provider, Collection<Long> userIds);

    Optional<ExternalAccount> findByUserIdAndProvider(Long userId, String provider);

    Optional<ExternalAccount> findByProviderAndExternalUserId(String provider, String externalUserId);

    List<ExternalAccount> findByUserId(Long userId);
}
