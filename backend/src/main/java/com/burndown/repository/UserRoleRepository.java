package com.burndown.repository;

import com.burndown.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUserId(Long userId);
    void deleteByUserIdAndRoleId(Long userId, Long roleId);
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId")
    List<Long> findRoleIdsByUserId(Long userId);

    // Optimized permission query — fetches all user permissions in one query to avoid N+1 queries.
    @Query("SELECT DISTINCT p.code FROM UserRole ur " +
           "JOIN Role r ON ur.roleId = r.id " +
           "JOIN r.permissions p " +
           "WHERE ur.userId = :userId")
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}
