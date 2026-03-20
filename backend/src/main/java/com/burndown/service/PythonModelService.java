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
 * Python 模型调用服务
 * 通过 ProcessBuilder 调用 Python 脚本进行模型推理
 */
@Slf4j
@Service
public class PythonModelService {

    @Value("${ml.python.executable:python}")
    private String pythonExecutable;

    private Path modelPath;
    private Path featureColumnsPath;
    private Path tempDir;

    //@PostConstruct 是 Java EE（现在是 Jakarta EE）提供的注解，在 Spring Boot 环境下，
    //它的核心作用就是确保在该 Bean 的依赖注入（Dependency Injection）完成后，立即执行初始化逻辑。
    @PostConstruct
    public void init() {
        try {
            // 创建临时目录
            tempDir = Files.createTempDirectory("sprint_model");

            // 复制模型文件到临时目录
            copyResourceToTemp("models/random_forest_model.pkl", "random_forest_model.pkl");

            /*
                基础时间与规模特征
                    • sprint_days: Sprint 的总持续天数（通常为 10 或 14 天）。
                    • days_elapsed: 当前 Sprint 已过去的天数。
                    • committed_sp: Sprint 计划时承诺的总故事点数（初始范围）。
                    • remaining_sp: 当前尚未完成（未达到 DoD）的故事点数。
                    • completed_sp: 当前已经完成并关闭的故事点数。
                2. 团队速率（Velocity）特征
                    • velocity_current: 当前 Sprint 的实时平均速率（已完成点数 / 已过去天数）。
                    • velocity_avg_5: 团队过去 5 个 Sprint 的平均交付速率（用于衡量团队长期稳定性）。
                    • velocity_std_5: 过去 5 个 Sprint 速率的标准差（用于衡量团队表现的波动性/可预测性）。
                3. 风险与团队状态特征
                    • blocked_stories: 当前处于“被阻碍（Blocked）”状态的故事数量或点数。
                    • attendance_rate: 团队成员的到岗率/出勤率（反映人力资源是否充足）。
                    • ratio_feature: 新功能开发在当前 Sprint 中的占比。
                    • ratio_bug: 缺陷修复（Bug）在当前 Sprint 中的占比。
                    • ratio_tech_debt: 技术债处理在当前 Sprint 中的占比。
                4. 派生/计算特征（用于模型增强）
                    • days_remaining: 剩余工作天数（sprint_days - days_elapsed）。
                    • elapsed_ratio: 时间进度占比（已过去天数 / 总天数）。
                    • remaining_ratio: 剩余工作量占比（剩余点数 / 承诺总点数）。
                    • velocity_gap: 速率差距（理想每日速率与当前实际速率的差值）。
                    • projected_sp: 基于当前速率预测的 Sprint 结束时能完成的总点数。
                projected_completion_ratio: 预测完成率（projected_sp / committed_sp）。
            * */
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
            // 在 Java 代码中，当你准备调用 Python 推理脚本时，你需要确保传入的特征数组（Feature Array）的顺序与这个 JSON 文件中定义的顺序完全一致。
            String featureVector = featureColumns.stream()
                    .map(col -> String.valueOf(features.getOrDefault(col, 0.0)))
                    .collect(Collectors.joining(","));

            // 调用 Python 脚本
            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutable,
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