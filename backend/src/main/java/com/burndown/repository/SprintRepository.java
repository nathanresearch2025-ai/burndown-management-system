package com.burndown.repository;

import com.burndown.entity.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Long> {
    List<Sprint> findByProjectId(Long projectId);
    List<Sprint> findByProjectIdOrderByStartDateDesc(Long projectId);

    /**
     * 查询项目最近完成的 Sprint（用于计算历史速度）
     */
    List<Sprint> findTop5ByProjectIdAndStatusOrderByEndDateDesc(Long projectId, Sprint.SprintStatus status);
}
