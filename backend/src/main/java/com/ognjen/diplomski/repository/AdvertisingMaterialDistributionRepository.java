package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.AdvertisingMaterialDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface AdvertisingMaterialDistributionRepository
        extends JpaRepository<AdvertisingMaterialDistribution, Long> {

    @Query("""
            SELECT distribution
            FROM AdvertisingMaterialDistribution distribution
            JOIN FETCH distribution.salesRep salesRep
            JOIN FETCH distribution.material
            JOIN FETCH distribution.customer
            WHERE UPPER(salesRep.role.name) = 'SALES_REP'
              AND distribution.distributedAt >= :start
              AND distribution.distributedAt < :end
            ORDER BY distribution.distributedAt DESC
            """)
    List<AdvertisingMaterialDistribution> findAllMonthly(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT distribution
            FROM AdvertisingMaterialDistribution distribution
            JOIN FETCH distribution.salesRep salesRep
            JOIN FETCH distribution.material
            JOIN FETCH distribution.customer
            WHERE salesRep.manager.id = :managerId
              AND UPPER(salesRep.role.name) = 'SALES_REP'
              AND distribution.distributedAt >= :start
              AND distribution.distributedAt < :end
            ORDER BY distribution.distributedAt DESC
            """)
    List<AdvertisingMaterialDistribution> findMonthlyByManagerTeam(
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT distribution
            FROM AdvertisingMaterialDistribution distribution
            JOIN FETCH distribution.salesRep
            JOIN FETCH distribution.material
            JOIN FETCH distribution.customer
            WHERE distribution.salesRep.id = :salesRepId
              AND distribution.distributedAt >= :start
              AND distribution.distributedAt < :end
            ORDER BY distribution.distributedAt DESC
            """)
    List<AdvertisingMaterialDistribution> findMonthlyBySalesRep(
            @Param("salesRepId") Long salesRepId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );
}
