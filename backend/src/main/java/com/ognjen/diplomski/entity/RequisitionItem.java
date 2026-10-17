package com.ognjen.diplomski.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "requisition_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_requisition_item_requisition_product",
                columnNames = {"requisition_id", "product_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class RequisitionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requisition_id", nullable = false)
    private Requisition requisition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(name = "stimulation_per_unit", nullable = false, precision = 10, scale = 2)
    private BigDecimal stimulationPerUnit;
}
