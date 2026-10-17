package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.AdvertisingMaterial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface AdvertisingMaterialRepository extends JpaRepository<AdvertisingMaterial, Long> {

    List<AdvertisingMaterial> findAllByActiveTrueOrderByNameAsc();

    List<AdvertisingMaterial> findAllByIdInAndActiveTrue(Collection<Long> ids);

    List<AdvertisingMaterial> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
