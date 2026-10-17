package com.ognjen.diplomski.entity;

import com.ognjen.diplomski.entity.enums.PackageType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "picture_url", nullable = false, length = 500)
    private String pictureUrl = "products/placeholder.webp";

    @Column(name = "volume_liters", nullable = false, precision = 10, scale = 3)
    private BigDecimal volumeLiters = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "package_type", nullable = false, length = 20)
    private PackageType packageType;

    @Column(name = "stimulation_per_unit", nullable = false, precision = 10, scale = 2)
    private BigDecimal stimulationPerUnit = BigDecimal.ZERO;

    @Column(name = "manager_stimulation_per_unit", nullable = false, precision = 10, scale = 2)
    private BigDecimal managerStimulationPerUnit = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
