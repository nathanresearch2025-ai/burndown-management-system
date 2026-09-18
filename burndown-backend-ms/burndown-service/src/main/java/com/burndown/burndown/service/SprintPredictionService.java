package com.burndown.burndown.service;

import com.burndown.burndown.entity.BurndownPoint;
import com.burndown.burndown.entity.SprintPrediction;
import com.burndown.burndown.repository.BurndownPointRepository;
import com.burndown.burndown.repository.SprintPredictionRepository;
import com.burndown.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SprintPredictionService {

    private final SprintPredictionRepository predictionRepository;
    private final BurndownPointRepository burndownPointRepository;

    @Cacheable(value = "predictions", key = "#sprintId")
    public SprintPrediction getBySprintId(Long sprintId) {
        return predictionRepository.findBySprintId(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("SprintPrediction", sprintId));
    }

    @Transactional
    @CacheEvict(value = "predictions", key = "#sprintId")
    public SprintPrediction computePrediction(Long sprintId) {
        List<BurndownPoint> points = burndownPointRepository
                .findBySprintIdOrderByRecordDateAsc(sprintId);

        SprintPrediction prediction = predictionRepository.findBySprintId(sprintId)
                .orElse(new SprintPrediction());
        prediction.setSprintId(sprintId);
        prediction.setModelVersion("v1.0");
        prediction.setMlModel("linear-regression");

        if (points.isEmpty()) {
            prediction.setPredictedCompletionRate(BigDecimal.ZERO);
            prediction.setRiskLevel("UNKNOWN");
            prediction.setNotes("No burndown data available");
        } else {
            BigDecimal rate = calculateCompletionRate(points);
            prediction.setPredictedCompletionRate(rate);
            prediction.setRiskLevel(assessRisk(rate));
            prediction.setNotes("Computed from " + points.size() + " data points");
        }

        return predictionRepository.save(prediction);
    }

    private BigDecimal calculateCompletionRate(List<BurndownPoint> points) {
        BurndownPoint last = points.get(points.size() - 1);
        if (last.getTotalPoints() == null || last.getTotalPoints().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal completed = last.getCompletedPoints() != null ? last.getCompletedPoints() : BigDecimal.ZERO;
        return completed.divide(last.getTotalPoints(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String assessRisk(BigDecimal rate) {
        if (rate.compareTo(BigDecimal.valueOf(70)) >= 0) return "LOW";
        if (rate.compareTo(BigDecimal.valueOf(40)) >= 0) return "MEDIUM";
        return "HIGH";
    }
}
