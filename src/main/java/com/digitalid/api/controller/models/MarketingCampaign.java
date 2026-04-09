package com.digitalid.api.controller.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "marketing_campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketingCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private MarketingTemplate template;

    /** DRAFT | SCHEDULED | SENT | CANCELLED */
    @Column(length = 20)
    private String status;

    /** ALL | OPTED_IN */
    @Column(name = "target_audience", length = 20)
    private String targetAudience;

    private LocalDateTime scheduledAt;
    private LocalDateTime sentAt;
    private Integer sentCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = LocalDateTime.now();
        if (status == null) status = "DRAFT";
        if (targetAudience == null) targetAudience = "OPTED_IN";
        if (sentCount == null) sentCount = 0;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
