package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.Customer;
import com.ognjen.diplomski.entity.enums.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsBySalesRepId(Long salesRepId);

    java.util.Optional<Customer> findByIdAndSalesRepIdAndStatus(
            Long id,
            Long salesRepId,
            CustomerStatus status
    );

    @Query(value = """
    SELECT customer
    FROM Customer customer
    JOIN FETCH customer.salesRep salesRep
    LEFT JOIN salesRep.manager manager
    WHERE customer.status IN :statuses
      AND (
            :accessAllCustomers = true
            OR (:accessManagedSalesReps = true AND manager.id = :principalId)
            OR (:accessOwnCustomers = true AND customer.salesRep.id = :principalId)
      )
      AND (
            :hasSearch = false
            OR LOWER(customer.objectName) LIKE CONCAT('%', :search, '%')
            OR LOWER(customer.companyName) LIKE CONCAT('%', :search, '%')
            OR LOWER(customer.pib) LIKE CONCAT('%', :search, '%')
            OR LOWER(COALESCE(customer.phone, '')) LIKE CONCAT('%', :search, '%')
            OR LOWER(customer.address) LIKE CONCAT('%', :search, '%')
      )
    """, countQuery = """
    SELECT COUNT(customer)
    FROM Customer customer
    JOIN customer.salesRep salesRep
    LEFT JOIN salesRep.manager manager
    WHERE customer.status IN :statuses
      AND (
            :accessAllCustomers = true
            OR (:accessManagedSalesReps = true AND manager.id = :principalId)
            OR (:accessOwnCustomers = true AND salesRep.id = :principalId)
      )
      AND (
            :hasSearch = false
            OR LOWER(customer.objectName) LIKE CONCAT('%', :search, '%')
            OR LOWER(customer.companyName) LIKE CONCAT('%', :search, '%')
            OR LOWER(customer.pib) LIKE CONCAT('%', :search, '%')
            OR LOWER(COALESCE(customer.phone, '')) LIKE CONCAT('%', :search, '%')
            OR LOWER(customer.address) LIKE CONCAT('%', :search, '%')
      )
    """)
    Page<Customer> search(
            @Param("statuses") Collection<CustomerStatus> statuses,
            @Param("principalId") Long principalId,
            @Param("accessAllCustomers") boolean accessAllCustomers,
            @Param("accessManagedSalesReps") boolean accessManagedSalesReps,
            @Param("accessOwnCustomers") boolean accessOwnCustomers,
            @Param("search") String search,
            @Param("hasSearch") boolean hasSearch,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(customer)
            FROM Customer customer
            WHERE customer.status = :status
              AND customer.salesRep.manager.id = :managerId
            """)
    long countByStatusForManagerTeam(
            @Param("status") CustomerStatus status,
            @Param("managerId") Long managerId
    );
}
