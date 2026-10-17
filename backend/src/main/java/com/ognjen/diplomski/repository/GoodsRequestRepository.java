package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.GoodsRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface GoodsRequestRepository extends JpaRepository<GoodsRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT goodsRequest
            FROM GoodsRequest goodsRequest
            WHERE goodsRequest.id = :id
            """)
    Optional<GoodsRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COUNT(DISTINCT goodsRequest.id)
            FROM GoodsRequest goodsRequest
            JOIN goodsRequest.items item
            WHERE item.status = com.ognjen.diplomski.entity.enums.GoodsRequestItemStatus.PENDING
            """)
    long countRequestsWithPendingItems();

    @Query("""
            SELECT DISTINCT goodsRequest
            FROM GoodsRequest goodsRequest
            JOIN FETCH goodsRequest.salesRep salesRep
            JOIN FETCH goodsRequest.items item
            LEFT JOIN FETCH item.product
            LEFT JOIN FETCH item.material
            WHERE UPPER(salesRep.role.name) = 'SALES_REP'
              AND goodsRequest.createdAt >= :start
              AND goodsRequest.createdAt < :end
              AND (item.product IS NOT NULL OR item.material IS NOT NULL)
            ORDER BY goodsRequest.createdAt DESC
            """)
    List<GoodsRequest> findAllMonthlyCatalogRequests(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT DISTINCT goodsRequest
            FROM GoodsRequest goodsRequest
            JOIN FETCH goodsRequest.salesRep
            JOIN FETCH goodsRequest.items item
            LEFT JOIN FETCH item.product
            LEFT JOIN FETCH item.material
            WHERE goodsRequest.salesRep.id = :salesRepId
              AND goodsRequest.createdAt >= :start
              AND goodsRequest.createdAt < :end
              AND (item.product IS NOT NULL OR item.material IS NOT NULL)
            ORDER BY goodsRequest.createdAt DESC
            """)
    List<GoodsRequest> findMonthlyCatalogRequestsBySalesRep(
            @Param("salesRepId") Long salesRepId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT DISTINCT goodsRequest
            FROM GoodsRequest goodsRequest
            JOIN FETCH goodsRequest.salesRep salesRep
            JOIN FETCH goodsRequest.items item
            LEFT JOIN FETCH item.product
            LEFT JOIN FETCH item.material
            WHERE salesRep.manager.id = :managerId
              AND UPPER(salesRep.role.name) = 'SALES_REP'
              AND goodsRequest.createdAt >= :start
              AND goodsRequest.createdAt < :end
              AND (item.product IS NOT NULL OR item.material IS NOT NULL)
            ORDER BY goodsRequest.createdAt DESC
            """)
    List<GoodsRequest> findMonthlyCatalogRequestsByManagerTeam(
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );
}
