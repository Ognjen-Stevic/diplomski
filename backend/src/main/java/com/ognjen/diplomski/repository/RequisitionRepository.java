package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RequisitionRepository extends JpaRepository<Requisition, Long> {

    long countByStatus(RequisitionStatus status);

    long countByStatusAndSalesRepId(RequisitionStatus status, Long salesRepId);

    @Query("""
            SELECT COUNT(requisition)
            FROM Requisition requisition
            WHERE requisition.status = :status
              AND requisition.salesRep.manager.id = :managerId
            """)
    long countByStatusForManagerTeam(
            @Param("status") RequisitionStatus status,
            @Param("managerId") Long managerId
    );

    @Query("""
            SELECT DISTINCT requisition
            FROM Requisition requisition
            JOIN FETCH requisition.customer
            JOIN FETCH requisition.salesRep salesRep
            LEFT JOIN FETCH salesRep.manager
            LEFT JOIN FETCH requisition.items item
            LEFT JOIN FETCH item.product
            WHERE requisition.id = :requisitionId
            """)
    Optional<Requisition> findDetailedById(@Param("requisitionId") Long requisitionId);

    @Query("""
            SELECT DISTINCT requisition
            FROM Requisition requisition
            JOIN FETCH requisition.customer
            JOIN FETCH requisition.salesRep
            LEFT JOIN FETCH requisition.items item
            LEFT JOIN FETCH item.product
            WHERE requisition.salesRep.id = :salesRepId
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            ORDER BY requisition.createdAt DESC
            """)
    List<Requisition> findMonthlyBySalesRep(
            @Param("salesRepId") Long salesRepId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT DISTINCT requisition
            FROM Requisition requisition
            JOIN FETCH requisition.customer
            JOIN FETCH requisition.salesRep salesRep
            LEFT JOIN FETCH salesRep.manager
            LEFT JOIN FETCH requisition.items item
            LEFT JOIN FETCH item.product
            WHERE salesRep.manager.id = :managerId
              AND requisition.status <> com.ognjen.diplomski.entity.enums.RequisitionStatus.DRAFT
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            ORDER BY requisition.createdAt DESC
            """)
    List<Requisition> findMonthlyByManagerTeam(
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT DISTINCT requisition
            FROM Requisition requisition
            JOIN FETCH requisition.customer
            JOIN FETCH requisition.salesRep salesRep
            LEFT JOIN FETCH salesRep.manager
            LEFT JOIN FETCH requisition.items item
            LEFT JOIN FETCH item.product
            WHERE requisition.createdAt >= :start
              AND requisition.createdAt < :end
            ORDER BY requisition.createdAt DESC
            """)
    List<Requisition> findAllMonthly(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT DISTINCT requisition
            FROM Requisition requisition
            JOIN FETCH requisition.customer
            JOIN FETCH requisition.salesRep
            LEFT JOIN FETCH requisition.items item
            LEFT JOIN FETCH item.product
            WHERE requisition.status IN :statuses
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            ORDER BY requisition.createdAt DESC
            """)
    List<Requisition> findMonthlyByStatuses(
            @Param("statuses") Set<RequisitionStatus> statuses,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT DISTINCT requisition
            FROM Requisition requisition
            JOIN FETCH requisition.customer
            JOIN FETCH requisition.salesRep salesRep
            LEFT JOIN FETCH salesRep.manager
            LEFT JOIN FETCH requisition.items item
            LEFT JOIN FETCH item.product
            WHERE requisition.customer.id = :customerId
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            ORDER BY requisition.createdAt DESC
            """)
    List<Requisition> findMonthlyByCustomer(
            @Param("customerId") Long customerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );
}
