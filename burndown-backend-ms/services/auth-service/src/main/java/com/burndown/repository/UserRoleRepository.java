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

    // 优化的权限查询 - 一次性获取用户所有权限，避免N+1查询
    @Query("SELECT DISTINCT p.code FROM UserRole ur " +
           "JOIN Role r ON ur.roleId = r.id " +
           "JOIN r.permissions p " +
           "WHERE ur.userId = :userId")
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}
