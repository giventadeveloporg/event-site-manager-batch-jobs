package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * Entity for tenant settings.
 * Mirrors the backend {@code tenant_settings} table in a simplified form for batch job processing.
 */
@Entity
@Table(name = "tenant_settings")
@Data
public class TenantSettings implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255, nullable = false, unique = true)
    private String tenantId;

    @Column(name = "allow_user_registration")
    private Boolean allowUserRegistration;

    @Column(name = "require_admin_approval")
    private Boolean requireAdminApproval;

    @Column(name = "enable_whatsapp_integration")
    private Boolean enableWhatsappIntegration;

    @Column(name = "enable_email_marketing")
    private Boolean enableEmailMarketing;

    @Column(name = "is_membership_subscription_enabled")
    private Boolean isMembershipSubscriptionEnabled;

    @Column(name = "whatsapp_api_key", length = 500)
    private String whatsappApiKey;

    @Column(name = "email_provider_config", length = 2048)
    private String emailProviderConfig;

    @Column(name = "max_events_per_month")
    private Integer maxEventsPerMonth;

    @Column(name = "max_attendees_per_event")
    private Integer maxAttendeesPerEvent;

    @Column(name = "enable_guest_registration")
    private Boolean enableGuestRegistration;

    @Column(name = "max_guests_per_attendee")
    private Integer maxGuestsPerAttendee;

    @Column(name = "default_event_capacity")
    private Integer defaultEventCapacity;

    @Column(name = "platform_fee_percentage", precision = 21, scale = 2)
    private BigDecimal platformFeePercentage;

    @Column(name = "custom_css", length = 8192)
    private String customCss;

    @Column(name = "custom_js", length = 16384)
    private String customJs;

    @Column(name = "show_events_section_in_home_page")
    private Boolean showEventsSectionInHomePage;

    @Column(name = "show_team_members_section_in_home_page")
    private Boolean showTeamMembersSectionInHomePage;

    @Column(name = "show_sponsors_section_in_home_page")
    private Boolean showSponsorsSectionInHomePage;

    @Column(name = "twilio_account_sid", length = 500)
    private String twilioAccountSid;

    @Column(name = "twilio_auth_token", length = 1048)
    private String twilioAuthToken;

    @Column(name = "twilio_whatsapp_from", length = 50)
    private String twilioWhatsappFrom;

    @Column(name = "whatsapp_webhook_url", length = 1048)
    private String whatsappWebhookUrl;

    @Column(name = "whatsapp_webhook_token", length = 1048)
    private String whatsappWebhookToken;

    @Column(name = "email_footer_html_url", length = 2048)
    private String emailFooterHtmlUrl;

    @Column(name = "email_header_image_url", length = 2048)
    private String emailHeaderImageUrl;

    @Column(name = "logo_image_url", length = 2048)
    private String logoImageUrl;

    @Column(name = "address_line_1", length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "city", length = 255)
    private String city;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "zip_code", length = 20)
    private String zipCode;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "state_province", length = 100)
    private String stateProvince;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "facebook_url", length = 1024)
    private String facebookUrl;

    @Column(name = "instagram_url", length = 1024)
    private String instagramUrl;

    @Column(name = "twitter_url", length = 1024)
    private String twitterUrl;

    @Column(name = "linkedin_url", length = 1024)
    private String linkedinUrl;

    @Column(name = "youtube_url", length = 1024)
    private String youtubeUrl;

    @Column(name = "tiktok_url", length = 1024)
    private String tiktokUrl;

    @Column(name = "homepage_cache_version", nullable = false)
    private Long homepageCacheVersion = 0L;

    @Column(name = "default_hero_image_urls_json", columnDefinition = "text")
    private String defaultHeroImageUrlsJson;

    @Column(name = "default_hero_display_mode", length = 32)
    private String defaultHeroDisplayMode;

    @Column(name = "default_hero_include_with_events")
    private Boolean defaultHeroIncludeWithEvents;

    @Column(name = "default_hero_max_display_count")
    private Integer defaultHeroMaxDisplayCount;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "tenant_organization_id")
    private Long tenantOrganizationId;
}
