package com.ognjen.diplomski.repository;

import com.ognjen.diplomski.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Short> {
    Optional<Role> findByNameIgnoreCase(String name);
}
