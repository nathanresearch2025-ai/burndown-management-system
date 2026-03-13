package com.burndown.service;

import lombok.extern.slf4j.Slf4j;
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
 * Python 模型调用服务
 * 通过 ProcessBuilder 调用 Python 脚本进行模型推理
 */
@Slf4j
@Service
public class PythonModelService {

    private Path modelPath;
    private Path featureColumnsPath;
    private Path tempDir;

    @PostConstruct
    public void init() {
        try {
            // 创建临时目录
            tempDir = Files.createTempDirectory("sprint_model");

            // 复制模型文件到临时目录
            copyResourceToTemp("models/random_forest_model.pkl", "random_forest_model.pkl");
            copyResourceToTemp("models/feature_columns.json", "feature_columns.json");

            modelPath = tempDir.resolve("random_forest_model.pkl");
            featureColumnsPath = tempDir.resolve("feature_columns.json");

            // 创建推理脚本
            createInferenceScript();

            log.info("Python 模型服务初始化完成，模型路径: {}", modelPath);
        } catch (Exception e) {
            log.error("Python 模型服务初始化失败", e);
        }
    }

    /**
     * 预测 Sprint 完成概率
     */
    public double predictSprintCompletion(Map<String, Double> features, List<String> featureColumns) {
        try {
            // 构建特征向量字符串
            String featureVector = featureColumns.stream()
                    .map(col -> String.valueOf(features.getOrDefault(col, 0.0)))
                    .collect(Collectors.joining(","));

            // 调用 Python 脚本
            ProcessBuilder pb = new ProcessBuilder(
                    "python",
                    tempDir.resolve("inference.py").toString(),
                    featureVector
            );
            pb.directory(tempDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python 脚本执行失败，退出码: {}, 输出: {}", exitCode, output.toString());
                return 0.5; // 默认概率
            }

            // 解析概率结果
            String result = output.toString().trim();
            String[] lines = result.split("\n");
            for (String line : lines) {
                if (line.startsWith("PROBABILITY:")) {
                    return Double.parseDouble(line.substring("PROBABILITY:".length()).trim());
                }
            }

            log.warn("无法解析 Python 脚本输出: {}", result);
            return 0.5;

        } catch (Exception e) {
            log.error("调用 Python 模型失败", e);
            return 0.5; // 降级返回默认概率
        }
    }

    /**
     * 复制资源文件到临时目录
     */
    private void copyResourceToTemp(String resourcePath, String fileName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        Path targetPath = tempDir.resolve(fileName);
        try (InputStream inputStream = resource.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 创建 Python 推理脚本
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
        print("ERROR: 需要特征向量参数")
        sys.exit(1)

    try:
        # 加载模型
        model = joblib.load('random_forest_model.pkl')

        # 加载特征列
        with open('feature_columns.json', 'r', encoding='utf-8') as f:
            config = json.load(f)
            feature_columns = config['feature_columns']

        # 解析特征向量
        feature_values = [float(x) for x in sys.argv[1].split(',')]

        if len(feature_values) != len(feature_columns):
            print(f"ERROR: 特征数量不匹配，期望 {len(feature_columns)}，实际 {len(feature_values)}")
            sys.exit(1)

        # 预测
        X = np.array([feature_values])
        probability = model.predict_proba(X)[0][1]  # 获取正类概率

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