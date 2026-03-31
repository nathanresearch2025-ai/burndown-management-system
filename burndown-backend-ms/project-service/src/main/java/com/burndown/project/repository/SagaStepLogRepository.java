package com.burndown.project.repository;

import com.burndown.project.entity.SagaStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaStepLogRepository extends JpaRepository<SagaStepLog, Long> {

    List<SagaStepLog> findBySagaIdOrderByExecutedAtAsc(String sagaId);
}
