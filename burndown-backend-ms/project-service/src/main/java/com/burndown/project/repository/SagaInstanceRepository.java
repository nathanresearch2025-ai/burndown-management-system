package com.burndown.project.repository;

import com.burndown.project.entity.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {

    Optional<SagaInstance> findBySprintIdAndStatusIn(Long sprintId, java.util.List<String> statuses);
}
