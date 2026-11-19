package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByActiveTrueOrderByNameAsc();

    List<Product> findAllByCodeInAndActiveTrue(Collection<String> codes);

    List<Product> findAllByCodeIn(Collection<String> codes);

    @Query("""
            SELECT product
            FROM Product product
            WHERE (
                :hasSearch = false
                OR LOWER(product.name) LIKE CONCAT('%', :search, '%')
                OR LOWER(product.code) LIKE CONCAT('%', :search, '%')
            )
            AND (:active IS NULL OR product.active = :active)
            """)
    Page<Product> searchManagement(
            @Param("search") String search,
            @Param("hasSearch") boolean hasSearch,
            @Param("active") Boolean active,
            Pageable pageable
    );

    boolean existsByCodeIgnoreCase(String code);

}
