package com.burndown.repository;

import com.burndown.entity.BurndownPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BurndownPointRepository extends JpaRepository<BurndownPoint, Long> {
    List<BurndownPoint> findBySprintIdOrderByPointDateAsc(Long sprintId);

    @Modifying
    void deleteBySprintId(Long sprintId);

    @Modifying
    void deleteBySprintIdAndPointDate(Long sprintId, LocalDate pointDate);

    Optional<BurndownPoint> findBySprintIdAndPointDate(Long sprintId, LocalDate pointDate);
}
