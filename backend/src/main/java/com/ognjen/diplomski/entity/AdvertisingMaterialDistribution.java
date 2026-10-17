package com.ognjen.diplomski.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "advertising_material_distribution")
@Getter
@Setter
@NoArgsConstructor
public class AdvertisingMaterialDistribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_rep_id", nullable = false)
    private AppUser salesRep;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_id", nullable = false)
    private AdvertisingMaterial material;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "distributed_at", nullable = false, updatable = false)
    private OffsetDateTime distributedAt;
}
