package com.burndown.burndown.controller;

import com.burndown.burndown.entity.SprintPrediction;
import com.burndown.burndown.service.SprintPredictionService;
import com.burndown.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
public class SprintPredictionController {

    private final SprintPredictionService predictionService;

    @GetMapping("/sprint/{sprintId}")
    public ResponseEntity<ApiResponse<SprintPrediction>> getBySprintId(@PathVariable Long sprintId) {
        return ResponseEntity.ok(ApiResponse.ok(predictionService.getBySprintId(sprintId)));
    }

    @PostMapping("/sprint/{sprintId}/compute")
    public ResponseEntity<ApiResponse<SprintPrediction>> compute(@PathVariable Long sprintId) {
        return ResponseEntity.ok(ApiResponse.ok(predictionService.computePrediction(sprintId)));
    }
}
