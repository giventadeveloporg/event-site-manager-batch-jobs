package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.TenantOrganization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantOrganizationRepository extends JpaRepository<TenantOrganization, Long> {

    Optional<TenantOrganization> findByTenantId(String tenantId);
}
