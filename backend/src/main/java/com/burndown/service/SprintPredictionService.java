package com.burndown.service;

import com.burndown.dto.SprintCompletionPredictionDto;
import com.burndown.entity.Sprint;
import com.burndown.entity.Task;
import com.burndown.exception.BusinessException;
import com.burndown.repository.SprintRepository;
import com.burndown.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint completion prediction service.
 * Uses a trained Random Forest model for predictions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SprintPredictionService {

    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final PythonModelService pythonModelService;

    private List<String> featureColumns;

    @PostConstruct
    public void init() {
        try {
            loadFeatureColumns();
            log.info("Sprint prediction service initialized successfully. Feature column count: {}", featureColumns.size());
        } catch (Exception e) {
            log.error("Sprint prediction service initialization failed", e);
        }
    }

    /**
     * Predict the completion probability for a Sprint — core business method.
     *
     * @param sprintId the unique identifier of the Sprint
     * @return SprintCompletionPredictionDto containing the predicted probability, risk level, and feature summary
     * @throws BusinessException 404 exception when the Sprint does not exist
     */
    public SprintCompletionPredictionDto predictSprintCompletion(Long sprintId) {
        // Step 1: Query the Sprint entity from the database.
        // Use Optional.orElseThrow() to ensure the Sprint exists; throw a business exception if not found.
        Sprint sprint = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new BusinessException("SPRINT_NOT_FOUND", "Sprint not found: " + sprintId, HttpStatus.NOT_FOUND));

        // Step 2: Feature engineering — compute the 19 feature values required by the ML model.
        // Includes time features, capacity features, velocity features, and task features.
        Map<String, Double> features = calculateFeatures(sprint);

        // Step 3: Model inference — invoke the Python Random Forest model for prediction.
        // Executes the Python script via ProcessBuilder, passing the feature vector, and returns completion probability [0.0, 1.0].
        double probability = pythonModelService.predictSprintCompletion(features, featureColumns);

        // Step 4: Risk level mapping — convert the numeric probability to a business-readable risk level.
        // GREEN: >=0.8, YELLOW: 0.5-0.8, RED: <0.5
        String riskLevel = mapRiskLevel(probability);

        // Step 5: Build feature summary — extract key features for frontend display and business analysis.
        // Includes time progress, remaining workload, velocity comparison, and other core metrics.
        SprintCompletionPredictionDto.FeatureSummary featureSummary = buildFeatureSummary(features);

        // Step 6: Build and return the complete prediction result object.
        // Use the Builder pattern to create an immutable response object.
        return SprintCompletionPredictionDto.builder()
                .probability(probability)           // completion probability [0.0, 1.0]
                .riskLevel(riskLevel)              // risk level: GREEN/YELLOW/RED
                .featureSummary(featureSummary)    // feature summary object
                .predictedAt(System.currentTimeMillis())  // prediction timestamp (milliseconds)
                .build();
    }

    /**
     * Calculate the Sprint feature vector — the core input for the ML model.
     *
     * Extracts 19 features from the Sprint entity and related data, divided into 4 groups:
     * 1. Time features (4): Sprint duration, elapsed time, remaining time, time-progress ratio
     * 2. Capacity features (4): committed story points, completed story points, remaining story points, completion ratio
     * 3. Velocity features (4): current velocity, historical average velocity, velocity std dev, velocity gap
     * 4. Task features (7): blocked task count, task type distribution, attendance rate, etc.
     *
     * @param sprint Sprint entity containing basic Sprint information
     * @return Map<String, Double> mapping of feature names to feature values (19 features total)
     */
    private Map<String, Double> calculateFeatures(Sprint sprint) {
        // Initialize the feature vector container.
        Map<String, Double> features = new HashMap<>();

        // ==================== Group 1: Basic time features ====================
        // Use the current date as the calculation reference point.
        LocalDate now = LocalDate.now();

        // Calculate total Sprint duration in days (start date to end date).
        long totalDays = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate());

        // Calculate elapsed days (start date to current date).
        long elapsedDays = ChronoUnit.DAYS.between(sprint.getStartDate(), now);

        // Calculate remaining days (current date to end date), clamped to non-negative.
        long remainingDays = Math.max(0, ChronoUnit.DAYS.between(now, sprint.getEndDate()));

        // Store time-related features.
        features.put("sprint_days", (double) totalDays);                                    // total Sprint days
        features.put("days_elapsed", (double) Math.max(1, elapsedDays));                  // elapsed days (min 1 to avoid divide-by-zero)
        features.put("days_remaining", (double) remainingDays);                           // remaining days
        features.put("elapsed_ratio", elapsedDays / (double) Math.max(1, totalDays));    // time-consumed ratio [0.0, 1.0]

        // ==================== Group 2: Sprint capacity features ====================
        // Extract story point information from the Sprint entity, handling null values and converting to double.
        /*
        Real-World Scenario Simulation
        Scenario Setup:
        A team is in a 10-working-day Sprint.
        • Committed Story Points: 50 points.
        • Ideal Velocity: 5 points per day on average.
        Phase A: Day 3 of the Sprint (Optimistic Forecast)
        • Status: 20 points completed, 30 points remaining.
        • Impact on Forecast:
        At this stage, the team’s actual burn rate is $20 / 3 = 6.67$ points/day. If this pace is maintained, the remaining 30 points will only take about 4.5 days to complete.
        • Conclusion: The forecast is highly optimistic. The team may even have the capacity to pull in the next task from the Backlog early.
        Phase B: Day 7 of the Sprint (Risk Warning)
        • Status: 25 points completed, 25 points remaining.
        • Impact on Forecast:
        70% of the time has elapsed, yet only 50% of the tasks are finished. The current actual velocity is $25 / 7 = 3.57$ points/day.
        At this rate, finishing the remaining 25 points would require 7 days, but only 3 days are left in the Sprint.
        Conclusion: A significant deviation has occurred. The Scrum Master needs to intervene at this point to coordinate "descoping"
        (moving non-core stories out of the Sprint) or reallocating resources; otherwise, the Sprint goal will not be met.
        * */

        double committedSp = sprint.getCommittedPoints() != null ? sprint.getCommittedPoints().doubleValue() : 0.0;  // initially committed story points
        double completedSp = sprint.getCompletedPoints() != null ? sprint.getCompletedPoints().doubleValue() : 0.0;  // completed story points
        double remainingSp = Math.max(0, committedSp - completedSp);                                                 // remaining story points (clamped to non-negative)

        // Store capacity-related features.
        features.put("committed_sp", committedSp);                                        // total committed story points
        features.put("completed_sp", completedSp);                                        // completed story points
        features.put("remaining_sp", remainingSp);                                        // remaining story points
        features.put("remaining_ratio", committedSp > 0 ? remainingSp / committedSp : 0.0);  // remaining work ratio

        // ==================== Group 3: Velocity features ====================
        // Calculate the current Sprint's development velocity (story points per day).
        double velocityCurrent = elapsedDays > 0 ? completedSp / elapsedDays : 0.0;

        // Calculate team average velocity and velocity stability from historical data.
        double velocityAvg = calculateHistoricalVelocity(sprint.getProjectId());          // average velocity over last 5 Sprints
        double velocityStd = calculateVelocityStd(sprint.getProjectId());                 // velocity std dev over last 5 Sprints

        // Store velocity-related features.
        features.put("velocity_current", velocityCurrent);                                // current velocity (story points/day)
        features.put("velocity_avg_5", velocityAvg);                                      // historical average velocity
        features.put("velocity_std_5", velocityStd);                                      // velocity std dev (stability indicator)
        features.put("velocity_gap", velocityCurrent - velocityAvg);                      // gap between current and historical average velocity

        // ==================== Group 4: Task features ====================
        // Query all tasks under the current Sprint to analyze task status and type distribution.
        List<Task> sprintTasks = taskRepository.findBySprintId(sprint.getId());

        // Count tasks in BLOCKED state — a key risk factor affecting Sprint completion.
        int blockedStories = (int) sprintTasks.stream()
                .filter(task -> "BLOCKED".equals(task.getStatus()))  // filter tasks with BLOCKED status
                .count();

        // Store blocked task feature.
        features.put("blocked_stories", (double) blockedStories);    // number of blocked tasks

        // Calculate task type distribution ratios — different task types have different completion difficulty and risk.
        long totalTasks = sprintTasks.size();                        // total task count
        if (totalTasks > 0) {
            // Count new-feature type tasks.
            long featureTasks = sprintTasks.stream()
                    .filter(task -> "FEATURE".equals(task.getType()))
                    .count();

            // Count bug-fix type tasks.
            long bugTasks = sprintTasks.stream()
                    .filter(task -> "BUG".equals(task.getType()))
                    .count();

            // Count technical-debt type tasks.
            long techDebtTasks = sprintTasks.stream()
                    .filter(task -> "TECH_DEBT".equals(task.getType()))
                    .count();

            // Calculate each task type ratio [0.0, 1.0].
            features.put("ratio_feature", featureTasks / (double) totalTasks);      // new-feature task ratio
            features.put("ratio_bug", bugTasks / (double) totalTasks);              // bug-fix task ratio
            features.put("ratio_tech_debt", techDebtTasks / (double) totalTasks);   // technical-debt task ratio
        } else {
            // If there are no tasks, set all ratios to 0.
            features.put("ratio_feature", 0.0);
            features.put("ratio_bug", 0.0);
            features.put("ratio_tech_debt", 0.0);
        }

        // Team attendance rate feature (currently simplified to a fixed value of 0.85).
        // TODO: Should be calculated from actual work logs (work_logs table) in a real implementation.
        // Attendance rate affects the team's effective development capacity and Sprint completion probability.
        features.put("attendance_rate", 0.85);

        // ==================== Group 5: Derived features ====================
        // Advanced features computed from existing features to provide stronger predictive power.

        // Projected final story points = already completed + what can be completed in remaining days at current velocity.
        double projectedSp = completedSp + velocityCurrent * remainingDays;

        // Store derived features.
        features.put("projected_sp", projectedSp);                                           // projected final story points completed
        features.put("projected_completion_ratio", committedSp > 0 ? projectedSp / committedSp : 0.0);  // projected completion ratio

        // Return the complete feature vector containing 19 features.
        return features;
    }

    /**
     * Calculate the historical average velocity.
     */
    private double calculateHistoricalVelocity(Long projectId) {
        List<Sprint> recentSprints = sprintRepository.findTop5ByProjectIdAndStatusOrderByEndDateDesc(
                projectId, Sprint.SprintStatus.COMPLETED);

        if (recentSprints.isEmpty()) {
            return 3.0; // default velocity
        }

        return recentSprints.stream()
                .filter(s -> s.getVelocity() != null)
                .mapToDouble(s -> s.getVelocity().doubleValue())
                .average()
                .orElse(3.0);
    }

    /**
     * Calculate the velocity standard deviation.
     */
    private double calculateVelocityStd(Long projectId) {
        List<Sprint> recentSprints = sprintRepository.findTop5ByProjectIdAndStatusOrderByEndDateDesc(
                projectId, Sprint.SprintStatus.COMPLETED);

        if (recentSprints.size() < 2) {
            return 1.0; // default std dev
        }

        double[] velocities = recentSprints.stream()
                .filter(s -> s.getVelocity() != null)
                .mapToDouble(s -> s.getVelocity().doubleValue())
                .toArray();

        if (velocities.length < 2) {
            return 1.0;
        }

        double mean = java.util.Arrays.stream(velocities).average().orElse(0.0);
        double variance = java.util.Arrays.stream(velocities)
                .map(v -> Math.pow(v - mean, 2))
                .average()
                .orElse(0.0);

        return Math.sqrt(variance);
    }

    /**
     * Map risk level — convert a numeric probability to a business-readable risk level.
     *
     * Risk level criteria:
     * - GREEN (low risk): completion probability >= 80% — Sprint is very likely to complete on time
     * - YELLOW (medium risk): completion probability 50%-80% — some risk, requires attention
     * - RED (high risk): completion probability < 50% — high risk of failure, immediate action needed
     *
     * @param probability model-predicted completion probability in range [0.0, 1.0]
     * @return String risk level identifier: GREEN, YELLOW, or RED
     */
    private String mapRiskLevel(double probability) {
        // High probability of completion (>=80%): GREEN level, team is in good shape.
        if (probability >= 0.8) {
            return "GREEN";
        }
        // Medium probability of completion (50%-80%): YELLOW level, requires monitoring and adjustment.
        else if (probability >= 0.5) {
            return "YELLOW";
        }
        // Low probability of completion (<50%): RED level, urgent intervention needed.
        else {
            return "RED";
        }
    }

    /**
     * Build feature summary — extract key features for frontend display and business analysis.
     *
     * Selects the 7 most important features from the full set of 19 to build the summary object.
     * These features are most meaningful to business stakeholders and help explain the prediction result.
     *
     * @param features the complete feature vector Map containing 19 features
     * @return FeatureSummary object containing 7 core indicators
     */
    private SprintCompletionPredictionDto.FeatureSummary buildFeatureSummary(Map<String, Double> features) {
        return SprintCompletionPredictionDto.FeatureSummary.builder()
                // Time-progress ratio: elapsed time as a proportion of total Sprint time.
                .daysElapsedRatio(features.get("elapsed_ratio"))
                // Remaining work ratio: remaining story points as a proportion of committed story points.
                .remainingRatio(features.get("remaining_ratio"))
                // Current velocity: story points completed per day in the current Sprint.
                .velocityCurrent(features.get("velocity_current"))
                // Historical average velocity: average delivery rate over the last 5 Sprints, used as a baseline.
                .velocityAvg(features.get("velocity_avg_5"))
                // Projected completion ratio: estimated final completion ratio at current velocity.
                .projectedCompletionRatio(features.get("projected_completion_ratio"))
                // Blocked task count: number of tasks currently blocked, a risk factor for progress.
                .blockedStories(features.get("blocked_stories").intValue())
                // Team attendance rate: attendance of team members, affects actual development capacity.
                .attendanceRate(features.get("attendance_rate"))
                .build();  // Build the immutable feature summary object.
    }

    /**
     * Load feature column configuration — reads the feature column order used during model training from a JSON file.
     *
     * ML models are sensitive to feature order; the order at inference time must exactly match the training order.
     * This method runs at service startup (@PostConstruct) and loads the feature column config into memory.
     *
     * Config file location: src/main/resources/models/feature_columns.json
     * File format: {"feature_columns": ["feature1", "feature2", ...]}
     *
     * @throws IOException when the config file is missing or malformed
     */
    private void loadFeatureColumns() throws IOException {
        // Load feature column config file from the classpath.
        ClassPathResource resource = new ClassPathResource("models/feature_columns.json");

        // Use try-with-resources to ensure the input stream is properly closed.
        try (InputStream inputStream = resource.getInputStream()) {
            JsonNode jsonNode = objectMapper.readTree(inputStream);
            featureColumns = objectMapper.convertValue(
                    jsonNode.get("feature_columns"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        }
    }
}