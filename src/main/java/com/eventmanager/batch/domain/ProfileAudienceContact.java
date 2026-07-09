package com.eventmanager.batch.domain;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;

@Entity
@Table(name = "profile_audience_contact")
@Data
public class ProfileAudienceContact implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "profileAudienceContactSeq")
    @SequenceGenerator(name = "profileAudienceContactSeq", sequenceName = "public.profile_audience_contact_id_seq", allocationSize = 1)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "tenant_id", length = 255)
    private String tenantId;

    @Column(name = "opt_in_status", length = 32)
    private String optInStatus;
}
