package com.burndown.controller;

import com.burndown.dto.SprintCompletionPredictionDto;
import com.burndown.service.SprintPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Sprint prediction controller.
 */
@Slf4j
@RestController
@RequestMapping("/sprints")
@RequiredArgsConstructor
@Tag(name = "Sprint Prediction", description = "Sprint completion prediction API")
public class SprintPredictionController {

    private final SprintPredictionService sprintPredictionService;

    @Operation(
            summary = "Predict Sprint completion probability",
            description = "Uses a machine learning model to predict the completion probability and risk level for a given Sprint"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prediction successful"),
            @ApiResponse(responseCode = "404", description = "Sprint not found"),
            @ApiResponse(responseCode = "500", description = "Prediction service error")
    })
    @GetMapping("/{id}/completion-probability")
    @PreAuthorize("hasAuthority('SPRINT:VIEW')")
    public ResponseEntity<SprintCompletionPredictionDto> getSprintCompletionProbability(
            @Parameter(description = "Sprint ID", required = true)
            @PathVariable Long id) {

        log.info("Predicting Sprint completion probability, Sprint ID: {}", id);

        // Call the service layer to execute prediction logic:
        // 1. Query Sprint info from the database.
        // 2. Compute the feature vector (19 features).
        // 3. Call the Python ML model for prediction.
        // 4. Return a result object containing probability, risk level, and feature summary.
        SprintCompletionPredictionDto prediction = sprintPredictionService.predictSprintCompletion(id);

        log.info("Sprint {} prediction complete, probability: {}, risk level: {}",
                id, prediction.getProbability(), prediction.getRiskLevel());

        // Return HTTP 200 with the prediction result JSON.
        return ResponseEntity.ok(prediction);
    }
}