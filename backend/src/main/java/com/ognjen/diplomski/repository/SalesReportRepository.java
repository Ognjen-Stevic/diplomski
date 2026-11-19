package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.enums.PackageType;
import com.ognjen.diplomski.entity.enums.GratisReason;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.math.BigDecimal;
import java.util.List;

public interface SalesReportRepository extends JpaRepository<Requisition, Long> {

    @Query("""
            SELECT product.code AS productCode,
                   product.name AS productName,
                   product.pictureUrl AS pictureUrl,
                   SUM(item.quantity) AS units,
                   product.volumeLiters AS volumeLiters,
                   product.packageType AS packageType,
                   product.managerStimulationPerUnit AS incentivePerItem,
                   SUM(item.quantity * product.managerStimulationPerUnit) AS totalIncentive
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.items item
            JOIN item.product product
            WHERE salesRep.manager.id = :managerId
              AND UPPER(salesRep.role.name) = 'SALES_REP'
              AND requisition.type = :type
              AND requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            GROUP BY product.id, product.code, product.name, product.pictureUrl,
                     product.volumeLiters, product.packageType, product.managerStimulationPerUnit
            ORDER BY product.name ASC
            """)
    List<TeamProductSalesProjection> findTeamProductAggregates(
            @Param("managerId") Long managerId,
            @Param("type") RequisitionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT MONTH(requisition.createdAt) AS month,
                   SUM(item.quantity * product.managerStimulationPerUnit) AS totalIncentive
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.items item
            JOIN item.product product
            WHERE salesRep.manager.id = :managerId
              AND UPPER(salesRep.role.name) = 'SALES_REP'
              AND requisition.type = :type
              AND requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            GROUP BY MONTH(requisition.createdAt)
            ORDER BY MONTH(requisition.createdAt) ASC
            """)
    List<TeamMonthlyIncentiveProjection> findTeamMonthlyIncentives(
            @Param("managerId") Long managerId,
            @Param("type") RequisitionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT product.code AS productCode,
                   product.name AS productName,
                   product.pictureUrl AS pictureUrl,
                   SUM(item.quantity) AS units,
                   product.volumeLiters AS volumeLiters,
                   product.packageType AS packageType,
                   SUM(item.quantity * product.stimulationPerUnit) AS salesRepIncentive,
                   SUM(item.quantity * product.managerStimulationPerUnit) AS managerIncentive
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.items item
            JOIN item.product product
            WHERE UPPER(salesRep.role.name) = 'SALES_REP'
              AND (:salesRepId IS NULL OR salesRep.id = :salesRepId)
              AND (:managerId IS NULL OR salesRep.manager.id = :managerId)
              AND requisition.type = :type
              AND requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            GROUP BY product.id, product.code, product.name, product.pictureUrl,
                     product.volumeLiters, product.packageType
            ORDER BY product.name ASC, product.code ASC
            """)
    List<ProductSalesAggregateProjection> findProductAggregates(
            @Param("salesRepId") Long salesRepId,
            @Param("managerId") Long managerId,
            @Param("type") RequisitionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT MONTH(requisition.createdAt) AS month,
                   SUM(item.quantity * product.stimulationPerUnit) AS salesRepIncentive,
                   SUM(item.quantity * product.managerStimulationPerUnit) AS managerIncentive
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.items item
            JOIN item.product product
            WHERE UPPER(salesRep.role.name) = 'SALES_REP'
              AND (:salesRepId IS NULL OR salesRep.id = :salesRepId)
              AND (:managerId IS NULL OR salesRep.manager.id = :managerId)
              AND requisition.type = :type
              AND requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            GROUP BY MONTH(requisition.createdAt)
            ORDER BY MONTH(requisition.createdAt) ASC
            """)
    List<MonthlyIncentiveAggregateProjection> findMonthlyIncentiveAggregates(
            @Param("salesRepId") Long salesRepId,
            @Param("managerId") Long managerId,
            @Param("type") RequisitionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT product.code AS productCode,
                   product.name AS productName,
                   product.pictureUrl AS pictureUrl,
                   SUM(item.quantity) AS units,
                   product.volumeLiters AS volumeLiters,
                   product.packageType AS packageType
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.items item
            JOIN item.product product
            WHERE UPPER(salesRep.role.name) = 'SALES_REP'
              AND (:salesRepId IS NULL OR salesRep.id = :salesRepId)
              AND (:managerId IS NULL OR salesRep.manager.id = :managerId)
              AND requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS
              AND requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            GROUP BY product.id, product.code, product.name, product.pictureUrl,
                     product.volumeLiters, product.packageType
            ORDER BY product.name ASC, product.code ASC
            """)
    List<GratisProductAggregateProjection> findGratisProductAggregates(
            @Param("salesRepId") Long salesRepId,
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT requisition.gratisReason AS reason,
                   COALESCE(SUM(item.quantity * product.volumeLiters), 0) AS totalLiters
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            LEFT JOIN requisition.items item
            LEFT JOIN item.product product
            WHERE UPPER(salesRep.role.name) = 'SALES_REP'
              AND (:salesRepId IS NULL OR salesRep.id = :salesRepId)
              AND (:managerId IS NULL OR salesRep.manager.id = :managerId)
              AND requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS
              AND requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            GROUP BY requisition.gratisReason
            """)
    List<GratisReasonAggregateProjection> findGratisReasonAggregates(
            @Param("salesRepId") Long salesRepId,
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT salesRep.id AS salesRepId,
                   salesRep.firstName AS firstName,
                   salesRep.lastName AS lastName,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.NORMAL
                        AND product.packageType = com.ognjen.diplomski.entity.enums.PackageType.BOTTLE
                       THEN item.quantity ELSE 0 END), 0) AS bottlesSold,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.NORMAL
                        AND product.packageType = com.ognjen.diplomski.entity.enums.PackageType.JUG
                       THEN item.quantity ELSE 0 END), 0) AS balloonsSold,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS
                        AND product.packageType = com.ognjen.diplomski.entity.enums.PackageType.BOTTLE
                       THEN item.quantity ELSE 0 END), 0) AS gratisBottles,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS
                        AND product.packageType = com.ognjen.diplomski.entity.enums.PackageType.JUG
                       THEN item.quantity ELSE 0 END), 0) AS gratisBalloons,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.NORMAL
                        AND item.id IS NOT NULL
                       THEN item.quantity * product.stimulationPerUnit ELSE 0 END), 0) AS stimulation,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.NORMAL
                        AND item.id IS NOT NULL
                       THEN item.quantity * product.managerStimulationPerUnit ELSE 0 END), 0)
                       AS managerStimulation
            FROM AppUser salesRep
            LEFT JOIN Requisition requisition
                   ON requisition.salesRep = salesRep
                  AND requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
                  AND requisition.createdAt >= :start
                  AND requisition.createdAt < :end
            LEFT JOIN requisition.items item
            LEFT JOIN item.product product
            WHERE salesRep.manager.id = :managerId
              AND UPPER(salesRep.role.name) = 'SALES_REP'
            GROUP BY salesRep.id, salesRep.firstName, salesRep.lastName
            ORDER BY salesRep.firstName ASC, salesRep.lastName ASC
            """)
    List<TeamSalesComparisonProjection> findTeamSalesComparison(
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT manager.id AS managerId,
                   manager.firstName AS firstName,
                   manager.lastName AS lastName,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.NORMAL
                        AND product.packageType = com.ognjen.diplomski.entity.enums.PackageType.BOTTLE
                       THEN item.quantity ELSE 0 END), 0) AS bottlesSold,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.NORMAL
                        AND product.packageType = com.ognjen.diplomski.entity.enums.PackageType.JUG
                       THEN item.quantity ELSE 0 END), 0) AS balloonsSold,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS
                        AND product.packageType = com.ognjen.diplomski.entity.enums.PackageType.BOTTLE
                       THEN item.quantity ELSE 0 END), 0) AS gratisBottles,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS
                        AND product.packageType = com.ognjen.diplomski.entity.enums.PackageType.JUG
                       THEN item.quantity ELSE 0 END), 0) AS gratisBalloons,
                   COALESCE(SUM(CASE
                       WHEN requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.NORMAL
                        AND item.id IS NOT NULL
                       THEN item.quantity * product.managerStimulationPerUnit ELSE 0 END), 0)
                       AS stimulation
            FROM AppUser manager
            LEFT JOIN AppUser salesRep
                   ON salesRep.manager = manager
                  AND UPPER(salesRep.role.name) = 'SALES_REP'
            LEFT JOIN Requisition requisition
                   ON requisition.salesRep = salesRep
                  AND requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
                  AND requisition.createdAt >= :start
                  AND requisition.createdAt < :end
            LEFT JOIN requisition.items item
            LEFT JOIN item.product product
            WHERE UPPER(manager.role.name) = 'MANAGER'
            GROUP BY manager.id, manager.firstName, manager.lastName
            ORDER BY manager.firstName ASC, manager.lastName ASC
            """)
    List<ManagerSalesComparisonProjection> findManagersSalesComparison(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    interface TeamProductSalesProjection {
        String getProductCode();

        String getProductName();

        String getPictureUrl();

        BigDecimal getUnits();

        BigDecimal getVolumeLiters();

        PackageType getPackageType();

        BigDecimal getIncentivePerItem();

        BigDecimal getTotalIncentive();
    }

    interface ProductSalesAggregateProjection {
        String getProductCode();

        String getProductName();

        String getPictureUrl();

        BigDecimal getUnits();

        BigDecimal getVolumeLiters();

        PackageType getPackageType();

        BigDecimal getSalesRepIncentive();

        BigDecimal getManagerIncentive();
    }

    interface MonthlyIncentiveAggregateProjection {
        Integer getMonth();

        BigDecimal getSalesRepIncentive();

        BigDecimal getManagerIncentive();
    }

    interface GratisProductAggregateProjection {
        String getProductCode();

        String getProductName();

        String getPictureUrl();

        BigDecimal getUnits();

        BigDecimal getVolumeLiters();

        PackageType getPackageType();
    }

    interface GratisReasonAggregateProjection {
        GratisReason getReason();

        BigDecimal getTotalLiters();
    }

    interface TeamMonthlyIncentiveProjection {
        Integer getMonth();

        BigDecimal getTotalIncentive();
    }

    interface TeamSalesComparisonProjection {
        Long getSalesRepId();

        String getFirstName();

        String getLastName();

        BigDecimal getBottlesSold();

        BigDecimal getBalloonsSold();

        BigDecimal getGratisBottles();

        BigDecimal getGratisBalloons();

        BigDecimal getStimulation();

        BigDecimal getManagerStimulation();
    }

    interface ManagerSalesComparisonProjection {
        Long getManagerId();

        String getFirstName();

        String getLastName();

        BigDecimal getBottlesSold();

        BigDecimal getBalloonsSold();

        BigDecimal getGratisBottles();

        BigDecimal getGratisBalloons();

        BigDecimal getStimulation();
    }
}
