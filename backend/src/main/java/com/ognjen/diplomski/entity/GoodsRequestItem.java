package com.ognjen.diplomski.entity;

import com.ognjen.diplomski.entity.enums.GoodsRequestItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "goods_request_item")
@Getter
@Setter
@NoArgsConstructor
public class GoodsRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_request_id", nullable = false)
    private GoodsRequest goodsRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id")
    private AdvertisingMaterial material;

    @Column(name = "custom_item_name", length = 200)
    private String customItemName;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GoodsRequestItemStatus status = GoodsRequestItemStatus.PENDING;

    @PrePersist
    @PreUpdate
    private void validateItemSource() {
        int sources = 0;
        if (product != null) sources++;
        if (material != null) sources++;
        if (customItemName != null && !customItemName.isBlank()) sources++;

        if (sources != 1) {
            throw new IllegalStateException(
                    "GoodsRequestItem mora imati tačno jedan izvor: product, material ili customItemName."
            );
        }
    }
}
