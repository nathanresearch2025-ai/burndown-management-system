package com.burndown.burndown.repository;

import com.burndown.burndown.entity.BurndownPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BurndownPointRepository extends JpaRepository<BurndownPoint, Long> {

    List<BurndownPoint> findBySprintIdOrderByRecordDateAsc(Long sprintId);

    Optional<BurndownPoint> findBySprintIdAndRecordDate(Long sprintId, LocalDate recordDate);

    @Query("SELECT b FROM BurndownPoint b WHERE b.sprintId = :sprintId AND b.recordDate BETWEEN :start AND :end ORDER BY b.recordDate ASC")
    List<BurndownPoint> findBySprintIdAndDateRange(
            @Param("sprintId") Long sprintId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    void deleteBySprintId(Long sprintId);
}
