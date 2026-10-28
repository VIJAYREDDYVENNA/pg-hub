package com.hostelmanagement.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface UserHostelAccessRepository extends JpaRepository<UserHostelAccess, UserHostelAccess.Key> {

    List<UserHostelAccess> findByUserId(Long userId);

    List<UserHostelAccess> findByUserIdIn(Collection<Long> userIds);

    @Modifying
    @Query("DELETE FROM UserHostelAccess a WHERE a.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
