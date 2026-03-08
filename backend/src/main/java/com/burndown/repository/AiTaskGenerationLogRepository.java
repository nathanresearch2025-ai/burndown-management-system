package com.burndown.repository;

import com.burndown.entity.AiTaskGenerationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiTaskGenerationLogRepository extends JpaRepository<AiTaskGenerationLog, Long> {
}
