package com.hostelmanagement.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = "defaultPermissions")
    Optional<Role> findByCode(String code);

    @EntityGraph(attributePaths = "defaultPermissions")
    List<Role> findAllByOrderByIdAsc();
}
