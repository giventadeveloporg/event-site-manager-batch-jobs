package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.time.ZonedDateTime;

@Entity
@Table(name = "profile_family_member")
@Data
public class ProfileFamilyMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "profileFamilyMemberSeq")
    @SequenceGenerator(name = "profileFamilyMemberSeq", sequenceName = "public.profile_family_member_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "tenant_id", length = 255, nullable = false)
    private String tenantId;

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Column(name = "relationship", length = 32, nullable = false)
    private String relationship;

    @Column(name = "role_title", length = 255)
    private String roleTitle;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "photo_url", length = 1024)
    private String photoUrl;

    @Column(name = "url", length = 500)
    private String url;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
