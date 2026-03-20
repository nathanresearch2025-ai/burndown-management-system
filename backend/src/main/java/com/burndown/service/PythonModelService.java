package com.burndown.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Python model invocation service.
 * Calls Python scripts via ProcessBuilder for model inference.
 */
@Slf4j
@Service
public class PythonModelService {

    @Value("${ml.python.executable:python}")
    private String pythonExecutable;

    private Path modelPath;
    private Path featureColumnsPath;
    private Path tempDir;

    // @PostConstruct is a Jakarta EE annotation. In Spring Boot, its core role is to ensure
    // that initialization logic runs immediately after dependency injection is complete for this bean.
    @PostConstruct
    public void init() {
        try {
            // Create a temporary directory.
            tempDir = Files.createTempDirectory("sprint_model");

            // Copy model files to the temporary directory.
            copyResourceToTemp("models/random_forest_model.pkl", "random_forest_model.pkl");

            /*
                1. Basic time and scale features
                    • sprint_days: Total duration of the Sprint in days (typically 10 or 14).
                    • days_elapsed: Number of days elapsed in the current Sprint.
                    • committed_sp: Total story points committed at Sprint planning (initial scope).
                    • remaining_sp: Story points not yet completed (Definition of Done not met).
                    • completed_sp: Story points that have been completed and closed.
                2. Team velocity features
                    • velocity_current: Real-time average velocity of the current Sprint (completed points / elapsed days).
                    • velocity_avg_5: Average delivery velocity over the last 5 Sprints (measures long-term team stability).
                    • velocity_std_5: Standard deviation of velocity over the last 5 Sprints (measures performance variability/predictability).
                3. Risk and team status features
                    • blocked_stories: Number or points of stories currently in Blocked state.
                    • attendance_rate: Team member attendance rate (reflects whether human resources are sufficient).
                    • ratio_feature: Proportion of new-feature development in the current Sprint.
                    • ratio_bug: Proportion of bug-fix work in the current Sprint.
                    • ratio_tech_debt: Proportion of technical debt work in the current Sprint.
                4. Derived/computed features (for model enhancement)
                    • days_remaining: Remaining working days (sprint_days - days_elapsed).
                    • elapsed_ratio: Time progress ratio (elapsed days / total days).
                    • remaining_ratio: Remaining workload ratio (remaining points / committed points).
                    • velocity_gap: Velocity gap (difference between ideal daily velocity and current actual velocity).
                    • projected_sp: Total points predicted to be completed by end of Sprint at current velocity.
                    • projected_completion_ratio: Predicted completion rate (projected_sp / committed_sp).
            * */
            copyResourceToTemp("models/feature_columns.json", "feature_columns.json");

            modelPath = tempDir.resolve("random_forest_model.pkl");
            featureColumnsPath = tempDir.resolve("feature_columns.json");

            // Create the inference script.
            createInferenceScript();

            log.info("Python model service initialized successfully, model path: {}", modelPath);
        } catch (Exception e) {
            log.error("Python model service initialization failed", e);
        }
    }

    /**
     * Predict the Sprint completion probability.
     */
    public double predictSprintCompletion(Map<String, Double> features, List<String> featureColumns) {
        try {
            // Build the feature vector string.
            // The feature array order must exactly match the order defined in feature_columns.json.
            String featureVector = featureColumns.stream()
                    .map(col -> String.valueOf(features.getOrDefault(col, 0.0)))
                    .collect(Collectors.joining(","));

            // Invoke the Python script.
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutable,
                    tempDir.resolve("inference.py").toString(),
                    featureVector
            );
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read the process output.
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python script execution failed, exit code: {}, output: {}", exitCode, output.toString());
                return 0.5; // default probability
            }

            // Parse the probability result.
            String result = output.toString().trim();
            String[] lines = result.split("\n");
            for (String line : lines) {
                if (line.startsWith("PROBABILITY:")) {
                    return Double.parseDouble(line.substring("PROBABILITY:".length()).trim());
                }
            }

            log.warn("Unable to parse Python script output: {}", result);
            return 0.5;

        } catch (Exception e) {
            log.error("Failed to invoke Python model", e);
            return 0.5; // fallback default probability
        }
    }

    /**
     * Copy a resource file to the temporary directory.
     */
    private void copyResourceToTemp(String resourcePath, String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        Path targetPath = tempDir.resolve(fileName);
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Create the Python inference script.
     */
    private void createInferenceScript() throws IOException {
        String script = """
import sys
import json
import joblib
import numpy as np
import warnings
warnings.filterwarnings('ignore')

def main():
    if len(sys.argv) != 2:
        print("ERROR: Feature vector argument required")
        sys.exit(1)

    try:
        # Load the model.
        model = joblib.load('random_forest_model.pkl')

        # Load feature columns.
        with open('feature_columns.json', 'r', encoding='utf-8') as f:
            config = json.load(f)
            feature_columns = config['feature_columns']

        # Parse the feature vector.
        feature_values = [float(x) for x in sys.argv[1].split(',')]

        if len(feature_values) != len(feature_columns):
            print(f"ERROR: Feature count mismatch, expected {len(feature_columns)}, got {len(feature_values)}")
            sys.exit(1)

        # Run prediction.
        X = np.array([feature_values])
        probability = model.predict_proba(X)[0][1]  # get positive class probability

        print(f"PROBABILITY:{probability:.6f}")

    except Exception as e:
        print(f"ERROR: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    main()
""";

        Path scriptPath = tempDir.resolve("inference.py");
        Files.write(scriptPath, script.getBytes());
    }
}