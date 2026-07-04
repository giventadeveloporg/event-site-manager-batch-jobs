package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * Multi-tenant organization configuration.
 * Mirrors the backend {@code tenant_organization} table for batch job processing.
 */
@Entity
@Table(name = "tenant_organization")
@Data
public class TenantOrganization implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tenantOrganizationSeq")
    @SequenceGenerator(name = "tenantOrganizationSeq", sequenceName = "public.tenant_organization_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255, nullable = false, unique = true)
    private String tenantId;

    @Column(name = "organization_name", length = 255, nullable = false)
    private String organizationName;

    @Column(name = "domain", length = 255, unique = true)
    private String domain;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

    @Column(name = "logo_url", length = 1024)
    private String logoUrl;

    @Column(name = "contact_email", length = 255, nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "address_line_1", length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 255)
    private String city;

    @Column(name = "state_province", length = 255)
    private String stateProvince;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "website_url", length = 1024)
    private String websiteUrl;

    @Column(name = "site_type", length = 32, nullable = false)
    private String siteType = "EVENT_ORG";

    @Column(name = "site_template_version", length = 32)
    private String siteTemplateVersion;

    @Column(name = "subscription_plan", length = 20)
    private String subscriptionPlan;

    @Column(name = "subscription_status", length = 20)
    private String subscriptionStatus;

    @Column(name = "subscription_start_date")
    private LocalDate subscriptionStartDate;

    @Column(name = "subscription_end_date")
    private LocalDate subscriptionEndDate;

    @Column(name = "monthly_fee_usd", precision = 21, scale = 2)
    private BigDecimal monthlyFeeUsd;

    @Column(name = "stripe_customer_id", length = 255)
    private String stripeCustomerId;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
