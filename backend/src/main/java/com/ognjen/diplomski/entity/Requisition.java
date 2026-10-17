package com.ognjen.diplomski.entity;

import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "requisition")
@Getter
@Setter
@NoArgsConstructor
public class Requisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_rep_id", nullable = false)
    private AppUser salesRep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequisitionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "gratis_reason", length = 30)
    private GratisReason gratisReason;

    @Column(name = "custom_reason", columnDefinition = "TEXT")
    private String customReason;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RequisitionStatus status = RequisitionStatus.DRAFT;

    @Column(name = "status_note", columnDefinition = "TEXT")
    private String statusNote;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RequisitionItem> items = new ArrayList<>();
}
