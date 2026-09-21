package com.burndown.auth.repository;

import com.burndown.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);
    void deleteByUserIdAndRoleId(Long userId, Long roleId);

    /** Single query: fetch all permission codes for a user (fixes N+1) */
    @Query("SELECT DISTINCT p.code FROM Role r " +
           "JOIN r.permissions p " +
           "WHERE r.id IN (SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId)")
    Set<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}
