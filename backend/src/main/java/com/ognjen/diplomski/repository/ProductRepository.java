package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Collection;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByActiveTrueOrderByNameAsc();

    List<Product> findAllByCodeInAndActiveTrue(Collection<String> codes);

    List<Product> findAllByCodeIn(Collection<String> codes);

    List<Product> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

}
