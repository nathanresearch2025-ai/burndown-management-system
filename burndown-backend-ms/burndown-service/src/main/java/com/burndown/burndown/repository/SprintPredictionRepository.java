package com.burndown.burndown.repository;

import com.burndown.burndown.entity.SprintPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SprintPredictionRepository extends JpaRepository<SprintPrediction, Long> {

    Optional<SprintPrediction> findBySprintId(Long sprintId);
}
