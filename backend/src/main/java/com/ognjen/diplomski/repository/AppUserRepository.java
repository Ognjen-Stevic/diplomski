package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.AppUser;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = {
            "role",
            "role.permissions"
    })
    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByManagerIdAndRoleNameIgnoreCase(Long managerId, String roleName);

    @Query("""
            SELECT employee
            FROM AppUser employee
            JOIN FETCH employee.role role
            LEFT JOIN FETCH employee.manager manager
            LEFT JOIN FETCH manager.role
            WHERE UPPER(role.name) IN :roleNames
            ORDER BY employee.firstName ASC, employee.lastName ASC
            """)
    List<AppUser> findManagedEmployees(@Param("roleNames") Collection<String> roleNames);

    @Query("""
            SELECT employee
            FROM AppUser employee
            JOIN FETCH employee.role
            LEFT JOIN FETCH employee.manager manager
            LEFT JOIN FETCH manager.role
            WHERE employee.id = :employeeId
            """)
    Optional<AppUser> findDetailedById(@Param("employeeId") Long employeeId);

    @EntityGraph(attributePaths = {"role"})
    List<AppUser> findAllByActiveTrueAndRoleNameIgnoreCaseOrderByFirstNameAscLastNameAsc(String roleName);

    Optional<AppUser> findByIdAndRoleNameIgnoreCase(Long id, String roleName);

    List<AppUser> findAllByManagerIdAndActiveTrueAndRoleNameIgnoreCaseOrderByFirstNameAscLastNameAsc(
            Long managerId,
            String roleName
    );

    Optional<AppUser> findByIdAndManagerIdAndActiveTrueAndRoleNameIgnoreCase(
            Long id,
            Long managerId,
            String roleName
    );

    Optional<AppUser> findByIdAndManagerIdAndRoleNameIgnoreCase(
            Long id,
            Long managerId,
            String roleName
    );

    @Query("""
            SELECT salesRep.id AS salesRepId,
                   salesRep.firstName AS firstName,
                   salesRep.lastName AS lastName,
                   salesRep.email AS email,
                   salesRep.phone AS phone,
                   salesRep.active AS active,
                   COUNT(DISTINCT customer.id) AS activeCustomers,
                   COUNT(DISTINCT requisition.id) AS currentMonthRequisitions
            FROM AppUser salesRep
            LEFT JOIN Customer customer
                   ON customer.salesRep = salesRep
                  AND customer.status = com.ognjen.diplomski.entity.enums.CustomerStatus.APPROVED
            LEFT JOIN Requisition requisition
                   ON requisition.salesRep = salesRep
                  AND requisition.createdAt >= :start
                  AND requisition.createdAt < :end
            WHERE salesRep.manager.id = :managerId
              AND UPPER(salesRep.role.name) = 'SALES_REP'
            GROUP BY salesRep.id, salesRep.firstName, salesRep.lastName,
                     salesRep.email, salesRep.phone, salesRep.active
            ORDER BY salesRep.firstName ASC, salesRep.lastName ASC
            """)
    List<TeamMemberSummaryProjection> findTeamMemberSummaries(
            @Param("managerId") Long managerId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            SELECT manager.id AS managerId,
                   manager.firstName AS firstName,
                   manager.lastName AS lastName,
                   manager.email AS email,
                   manager.phone AS phone,
                   manager.active AS active,
                   COUNT(DISTINCT salesRep.id) AS teamMemberCount,
                   COUNT(DISTINCT customer.id) AS activeCustomers,
                   COUNT(DISTINCT requisition.id) AS currentMonthRequisitions
            FROM AppUser manager
            LEFT JOIN AppUser salesRep
                   ON salesRep.manager = manager
                  AND UPPER(salesRep.role.name) = 'SALES_REP'
            LEFT JOIN Customer customer
                   ON customer.salesRep = salesRep
                  AND customer.status = com.ognjen.diplomski.entity.enums.CustomerStatus.APPROVED
            LEFT JOIN Requisition requisition
                   ON requisition.salesRep = salesRep
                  AND requisition.createdAt >= :start
                  AND requisition.createdAt < :end
            WHERE UPPER(manager.role.name) = 'MANAGER'
            GROUP BY manager.id, manager.firstName, manager.lastName,
                     manager.email, manager.phone, manager.active
            ORDER BY manager.firstName ASC, manager.lastName ASC
            """)
    List<ManagerTeamSummaryProjection> findManagerTeamSummaries(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    interface TeamMemberSummaryProjection {
        Long getSalesRepId();

        String getFirstName();

        String getLastName();

        String getEmail();

        String getPhone();

        Boolean getActive();

        Long getActiveCustomers();

        Long getCurrentMonthRequisitions();
    }

    interface ManagerTeamSummaryProjection {
        Long getManagerId();

        String getFirstName();

        String getLastName();

        String getEmail();

        String getPhone();

        Boolean getActive();

        Long getTeamMemberCount();

        Long getActiveCustomers();

        Long getCurrentMonthRequisitions();
    }
}
