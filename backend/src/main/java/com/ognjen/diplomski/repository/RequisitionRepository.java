package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.dto.DashboardMonthlyStatisticsResponse;
import com.ognjen.diplomski.dto.RequisitionListSummaryResponse;
import com.ognjen.diplomski.entity.Requisition;
import com.ognjen.diplomski.entity.enums.RequisitionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.ognjen.diplomski.entity.enums.RequisitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

public interface RequisitionRepository extends JpaRepository<Requisition, Long> {

    // Lock only the parent row: PostgreSQL cannot lock the DISTINCT/outer-join detail query.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.QueryHints(@jakarta.persistence.QueryHint(
            name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT requisition FROM Requisition requisition WHERE requisition.id = :id")
    Optional<Requisition> findForUpdate(@Param("id") Long id);


    long countByStatus(RequisitionStatus status);

    long countByStatusAndSalesRepId(RequisitionStatus status, Long salesRepId);

    @Query("""
            SELECT new com.ognjen.diplomski.dto.DashboardMonthlyStatisticsResponse(
                COUNT(CASE WHEN requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED THEN 1 END),
                COUNT(CASE WHEN requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
                    AND requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS THEN 1 END),
                COUNT(CASE WHEN requisition.status IN (
                    com.ognjen.diplomski.entity.enums.RequisitionStatus.CANCELLED_BY_MANAGER,
                    com.ognjen.diplomski.entity.enums.RequisitionStatus.CANCELLED_BY_BOOKKEEPER
                ) THEN 1 END)
            )
            FROM Requisition requisition
            WHERE requisition.createdAt >= :start
              AND requisition.createdAt < :end
            """)
    DashboardMonthlyStatisticsResponse getMonthlyStatistics(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT new com.ognjen.diplomski.dto.DashboardMonthlyStatisticsResponse(
                COUNT(CASE WHEN requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED THEN 1 END),
                COUNT(CASE WHEN requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
                    AND requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS THEN 1 END),
                COUNT(CASE WHEN requisition.status IN (
                    com.ognjen.diplomski.entity.enums.RequisitionStatus.CANCELLED_BY_MANAGER,
                    com.ognjen.diplomski.entity.enums.RequisitionStatus.CANCELLED_BY_BOOKKEEPER
                ) THEN 1 END)
            )
            FROM Requisition requisition
            WHERE requisition.salesRep.id = :salesRepId
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            """)
    DashboardMonthlyStatisticsResponse getMonthlyStatisticsBySalesRep(
            @Param("salesRepId") Long salesRepId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT new com.ognjen.diplomski.dto.DashboardMonthlyStatisticsResponse(
                COUNT(CASE WHEN requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED THEN 1 END),
                COUNT(CASE WHEN requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
                    AND requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS THEN 1 END),
                COUNT(CASE WHEN requisition.status IN (
                    com.ognjen.diplomski.entity.enums.RequisitionStatus.CANCELLED_BY_MANAGER,
                    com.ognjen.diplomski.entity.enums.RequisitionStatus.CANCELLED_BY_BOOKKEEPER
                ) THEN 1 END)
            )
            FROM Requisition requisition
            WHERE requisition.salesRep.manager.id = :managerId
              AND requisition.status <> com.ognjen.diplomski.entity.enums.RequisitionStatus.DRAFT
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            """)
    DashboardMonthlyStatisticsResponse getMonthlyStatisticsForManagerTeam(
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT new com.ognjen.diplomski.dto.DashboardMonthlyStatisticsResponse(
                COUNT(CASE WHEN requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED THEN 1 END),
                COUNT(CASE WHEN requisition.status = com.ognjen.diplomski.entity.enums.RequisitionStatus.COMPLETED
                    AND requisition.type = com.ognjen.diplomski.entity.enums.RequisitionType.GRATIS THEN 1 END),
                COUNT(CASE WHEN requisition.status IN (
                    com.ognjen.diplomski.entity.enums.RequisitionStatus.CANCELLED_BY_MANAGER,
                    com.ognjen.diplomski.entity.enums.RequisitionStatus.CANCELLED_BY_BOOKKEEPER
                ) THEN 1 END)
            )
            FROM Requisition requisition
            WHERE requisition.status IN :statuses
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            """)
    DashboardMonthlyStatisticsResponse getMonthlyStatisticsByStatuses(
            @Param("statuses") Set<RequisitionStatus> statuses,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query(value = """
            SELECT new com.ognjen.diplomski.dto.RequisitionListSummaryResponse(
                requisition.id,
                salesRep.id, salesRep.firstName, salesRep.lastName, salesRep.email,
                customer.id, customer.objectName, customer.companyName, customer.email,
                requisition.type, requisition.createdAt, requisition.status
            )
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.customer customer
            WHERE requisition.createdAt >= :start
              AND requisition.createdAt < :end
              AND (:customerId IS NULL OR customer.id = :customerId)
              AND (:status IS NULL OR requisition.status = :status)
              AND (:searchEnabled = FALSE
                   OR LOWER(customer.objectName) LIKE :searchPattern
                   OR LOWER(customer.companyName) LIKE :searchPattern)
            """,
            countQuery = """
            SELECT COUNT(requisition)
            FROM Requisition requisition
            JOIN requisition.customer customer
            WHERE requisition.createdAt >= :start
              AND requisition.createdAt < :end
              AND (:customerId IS NULL OR customer.id = :customerId)
              AND (:status IS NULL OR requisition.status = :status)
              AND (:searchEnabled = FALSE
                   OR LOWER(customer.objectName) LIKE :searchPattern
                   OR LOWER(customer.companyName) LIKE :searchPattern)
            """)
    Page<RequisitionListSummaryResponse> findMonthlyPage(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("customerId") Long customerId,
            @Param("status") RequisitionStatus status,
            @Param("searchEnabled") boolean searchEnabled,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @Query(value = """
            SELECT new com.ognjen.diplomski.dto.RequisitionListSummaryResponse(
                requisition.id,
                salesRep.id, salesRep.firstName, salesRep.lastName, salesRep.email,
                customer.id, customer.objectName, customer.companyName, customer.email,
                requisition.type, requisition.createdAt, requisition.status
            )
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.customer customer
            WHERE salesRep.id = :salesRepId
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
              AND (:customerId IS NULL OR customer.id = :customerId)
              AND (:status IS NULL OR requisition.status = :status)
              AND (:searchEnabled = FALSE
                   OR LOWER(customer.objectName) LIKE :searchPattern
                   OR LOWER(customer.companyName) LIKE :searchPattern)
            """,
            countQuery = """
            SELECT COUNT(requisition)
            FROM Requisition requisition
            JOIN requisition.customer customer
            WHERE requisition.salesRep.id = :salesRepId
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
              AND (:customerId IS NULL OR customer.id = :customerId)
              AND (:status IS NULL OR requisition.status = :status)
              AND (:searchEnabled = FALSE
                   OR LOWER(customer.objectName) LIKE :searchPattern
                   OR LOWER(customer.companyName) LIKE :searchPattern)
            """)
    Page<RequisitionListSummaryResponse> findMonthlyPageBySalesRep(
            @Param("salesRepId") Long salesRepId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("customerId") Long customerId,
            @Param("status") RequisitionStatus status,
            @Param("searchEnabled") boolean searchEnabled,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @Query(value = """
            SELECT new com.ognjen.diplomski.dto.RequisitionListSummaryResponse(
                requisition.id,
                salesRep.id, salesRep.firstName, salesRep.lastName, salesRep.email,
                customer.id, customer.objectName, customer.companyName, customer.email,
                requisition.type, requisition.createdAt, requisition.status
            )
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.customer customer
            WHERE salesRep.manager.id = :managerId
              AND requisition.status <> com.ognjen.diplomski.entity.enums.RequisitionStatus.DRAFT
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
              AND (:customerId IS NULL OR customer.id = :customerId)
              AND (:status IS NULL OR requisition.status = :status)
              AND (:searchEnabled = FALSE
                   OR LOWER(customer.objectName) LIKE :searchPattern
                   OR LOWER(customer.companyName) LIKE :searchPattern)
            """,
            countQuery = """
            SELECT COUNT(requisition)
            FROM Requisition requisition
            JOIN requisition.customer customer
            WHERE requisition.salesRep.manager.id = :managerId
              AND requisition.status <> com.ognjen.diplomski.entity.enums.RequisitionStatus.DRAFT
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
              AND (:customerId IS NULL OR customer.id = :customerId)
              AND (:status IS NULL OR requisition.status = :status)
              AND (:searchEnabled = FALSE
                   OR LOWER(customer.objectName) LIKE :searchPattern
                   OR LOWER(customer.companyName) LIKE :searchPattern)
            """)
    Page<RequisitionListSummaryResponse> findMonthlyPageForManagerTeam(
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("customerId") Long customerId,
            @Param("status") RequisitionStatus status,
            @Param("searchEnabled") boolean searchEnabled,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @Query(value = """
            SELECT new com.ognjen.diplomski.dto.RequisitionListSummaryResponse(
                requisition.id,
                salesRep.id, salesRep.firstName, salesRep.lastName, salesRep.email,
                customer.id, customer.objectName, customer.companyName, customer.email,
                requisition.type, requisition.createdAt, requisition.status
            )
            FROM Requisition requisition
            JOIN requisition.salesRep salesRep
            JOIN requisition.customer customer
            WHERE requisition.status IN :visibleStatuses
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
              AND (:customerId IS NULL OR customer.id = :customerId)
              AND (:status IS NULL OR requisition.status = :status)
              AND (:searchEnabled = FALSE
                   OR LOWER(customer.objectName) LIKE :searchPattern
                   OR LOWER(customer.companyName) LIKE :searchPattern)
            """,
            countQuery = """
            SELECT COUNT(requisition)
            FROM Requisition requisition
            JOIN requisition.customer customer
            WHERE requisition.status IN :visibleStatuses
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
              AND (:customerId IS NULL OR customer.id = :customerId)
              AND (:status IS NULL OR requisition.status = :status)
              AND (:searchEnabled = FALSE
                   OR LOWER(customer.objectName) LIKE :searchPattern
                   OR LOWER(customer.companyName) LIKE :searchPattern)
            """)
    Page<RequisitionListSummaryResponse> findMonthlyPageByStatuses(
            @Param("visibleStatuses") Set<RequisitionStatus> visibleStatuses,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("customerId") Long customerId,
            @Param("status") RequisitionStatus status,
            @Param("searchEnabled") boolean searchEnabled,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(requisition)
            FROM Requisition requisition
            WHERE requisition.type = :type
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            """)
    long countMonthlyByType(
            @Param("type") RequisitionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT COUNT(requisition)
            FROM Requisition requisition
            WHERE requisition.salesRep.id = :salesRepId
              AND requisition.type = :type
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            """)
    long countMonthlyBySalesRepAndType(
            @Param("salesRepId") Long salesRepId,
            @Param("type") RequisitionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT COUNT(requisition)
            FROM Requisition requisition
            WHERE requisition.salesRep.manager.id = :managerId
              AND requisition.status <> com.ognjen.diplomski.entity.enums.RequisitionStatus.DRAFT
              AND requisition.type = :type
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            """)
    long countMonthlyForManagerTeamByType(
            @Param("managerId") Long managerId,
            @Param("type") RequisitionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT COUNT(requisition)
            FROM Requisition requisition
            WHERE requisition.status IN :statuses
              AND requisition.type = :type
              AND requisition.createdAt >= :start
              AND requisition.createdAt < :end
            """)
    long countMonthlyByStatusesAndType(
            @Param("statuses") Set<RequisitionStatus> statuses,
            @Param("type") RequisitionType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

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

}
