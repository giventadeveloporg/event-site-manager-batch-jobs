package com.eventmanager.batch.repository;

import com.eventmanager.batch.domain.ProfileAudienceContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileAudienceContactRepository extends JpaRepository<ProfileAudienceContact, Long> {
    @Query(
        "SELECT DISTINCT p.email FROM ProfileAudienceContact p " +
        "WHERE p.tenantId = :tenantId " +
        "AND p.optInStatus = 'OPTED_IN' " +
        "AND p.email IS NOT NULL " +
        "AND p.email <> ''"
    )
    List<String> findOptedInEmailsByTenantId(@Param("tenantId") String tenantId);
}
