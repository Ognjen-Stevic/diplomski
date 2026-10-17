package com.ognjen.diplomski.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "goods_request")
@Getter
@Setter
@NoArgsConstructor
public class GoodsRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_rep_id", nullable = false)
    private AppUser salesRep;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "goodsRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GoodsRequestItem> items = new ArrayList<>();

    public void addItem(GoodsRequestItem item) {
        items.add(item);
        item.setGoodsRequest(this);
    }

    public void removeItem(GoodsRequestItem item) {
        items.remove(item);
        item.setGoodsRequest(null);
    }
}
