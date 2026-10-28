package com.hostelmanagement.auth;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"roles", "permissions"})
    Optional<User> findWithAccessById(Long id);

    @EntityGraph(attributePaths = {"roles", "permissions"})
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRoles_Code(String code);

    @EntityGraph(attributePaths = {"roles"})
    List<User> findByOwnerIdOrderByFullName(Long ownerId);

    Optional<User> findByIdAndOwnerId(Long id, Long ownerId);

    /** Users of an owner that may see a hostel: the owner(s) plus users granted access to it. */
    @Query("""
            SELECT DISTINCT u.id FROM User u JOIN u.roles r
            WHERE u.ownerId = :ownerId AND u.enabled = true AND (r.code = 'OWNER' OR u.id IN
              (SELECT a.userId FROM UserHostelAccess a WHERE a.hostelId = :hostelId))""")
    List<Long> findUserIdsWithHostelAccess(@Param("ownerId") Long ownerId, @Param("hostelId") Long hostelId);

    @Query("""
            SELECT DISTINCT u.id FROM User u JOIN u.roles r
            WHERE u.ownerId = :ownerId AND u.enabled = true AND r.code = 'OWNER'""")
    List<Long> findOwnerUserIds(@Param("ownerId") Long ownerId);
}
