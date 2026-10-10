# Sprint健康度评估 - 六维度核心算法详解

## 概述

本文档详细说明Sprint健康度评估系统中六个维度的核心算法原理、计算公式和伪代码实现。

---

## 维度1：燃尽趋势 (Burndown Trend)

### 算法原理

燃尽趋势评估通过对比实际燃尽线与理想燃尽线的偏差，来判断Sprint进度是否健康。

**理想燃尽线**：假设工作均匀分布在整个Sprint期间完成。  
**实际燃尽线**：基于每日剩余工作量的真实记录。

### 计算公式

```
1. 计算理想剩余工作量
   idealRemaining = totalStoryPoints × (1 - elapsedDays / totalDays)

2. 获取实际剩余工作量
   actualRemaining = 最新BurndownPoint的remainingWork

3. 计算偏差百分比
   deviation = |actualRemaining - idealRemaining| / idealRemaining × 100

4. 计算惩罚因子
   penaltyFactor = actualRemaining > idealRemaining ? 1.5 : 2.0
   // 落后惩罚较轻(1.5)，提前惩罚较重(2.0)

5. 计算得分
   score = max(0, 100 - deviation × penaltyFactor)
```

### 评分阈值

| 偏差范围 | 得分范围 | 健康状态 |
|---------|---------|---------|
| < 5% | 90-100 | EXCELLENT |
| 5-10% | 80-90 | GOOD |
| 10-20% | 60-80 | MODERATE |
| 20-30% | 40-60 | WARNING |
| > 30% | 0-40 | CRITICAL |

### 伪代码实现

```pseudocode
function calculateBurndownTrend(sprint, tasks, burndownPoints):
    // 1. 基础数据检查
    if burndownPoints.isEmpty():
        return {
            score: 100,
            status: "GOOD",
            description: "暂无燃尽数据"
        }
    
    // 2. 计算时间进度
    totalDays = daysBetween(sprint.startDate, sprint.endDate)
    elapsedDays = daysBetween(sprint.startDate, today())
    
    // 3. 计算总工作量（故事点）
    totalStoryPoints = sum(tasks, task => task.storyPoints)
    
    // 4. 计算理想剩余工作量
    progressRatio = elapsedDays / totalDays
    idealRemaining = totalStoryPoints × (1 - progressRatio)
    
    // 5. 获取实际剩余工作量
    latestPoint = burndownPoints[burndownPoints.length - 1]
    actualRemaining = latestPoint.remainingWork
    
    // 6. 计算偏差
    deviation = abs(actualRemaining - idealRemaining) / idealRemaining × 100
    
    // 7. 确定惩罚因子
    if actualRemaining > idealRemaining:
        penaltyFactor = 1.5  // 落后：较轻惩罚
    else:
        penaltyFactor = 2.0  // 提前：较重惩罚
    
    // 8. 计算得分
    score = max(0, 100 - deviation × penaltyFactor)
    
    // 9. 计算趋势斜率（线性回归）
    trendSlope = calculateLinearRegressionSlope(burndownPoints)
    
    // 10. 确定健康状态
    status = determineStatus(score)
    
    // 11. 生成描述
    description = generateDescription(deviation, score)
    
    return {
        score: score,
        status: status,
        details: {
            idealRemaining: idealRemaining,
            actualRemaining: actualRemaining,
            deviation: (actualRemaining - idealRemaining) / idealRemaining × 100,
            trendSlope: trendSlope
        },
        description: description
    }

// 计算线性回归斜率
function calculateLinearRegressionSlope(burndownPoints):
    n = burndownPoints.length
    sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0
    
    for i from 0 to n-1:
        x = i  // 天数索引
        y = burndownPoints[i].remainingWork
        sumX += x
        sumY += y
        sumXY += x × y
        sumX2 += x × x
    
    slope = (n × sumXY - sumX × sumY) / (n × sumX2 - sumX × sumX)
    return slope
```

### 示例计算

**场景：Sprint第5天评估**

```
输入：
- totalStoryPoints = 100
- totalDays = 10
- elapsedDays = 5
- actualRemaining = 60

计算：
1. idealRemaining = 100 × (1 - 5/10) = 50
2. deviation = |60 - 50| / 50 × 100 = 20%
3. penaltyFactor = 1.5 (落后)
4. score = 100 - 20 × 1.5 = 70

结果：
- 得分：70分
- 状态：MODERATE
- 描述：实际进度与理想线存在偏差
```

---

## 维度2：任务完成率 (Task Completion Rate)

### 算法原理

通过对比实际完成率与预期完成率，评估任务交付进度是否符合计划。

### 计算公式

```
1. 计算预期完成率
   expectedRate = elapsedDays / totalDays × 100

2. 计算实际完成率
   actualRate = completedTasks / totalTasks × 100

3. 计算完成率比例
   ratio = actualRate / expectedRate

4. 计算得分
   score = min(100, ratio × 100)
```

### 评分阈值

| 实际 vs 预期 | 得分范围 | 健康状态 |
|-------------|---------|---------|
| 实际 ≥ 预期+10% | 90-100 | EXCELLENT |
| 实际 ≥ 预期 | 75-90 | GOOD |
| 实际 ≥ 预期-10% | 60-75 | MODERATE |
| 实际 ≥ 预期-20% | 40-60 | WARNING |
| 实际 < 预期-20% | 0-40 | CRITICAL |

### 伪代码实现

```pseudocode
function calculateTaskCompletion(sprint, tasks):
    // 1. 统计任务数量
    totalTasks = tasks.length
    completedTasks = count(tasks, task => task.status == "DONE")
    
    // 2. 计算实际完成率
    actualRate = (completedTasks / totalTasks) × 100
    
    // 3. 计算时间进度
    totalDays = daysBetween(sprint.startDate, sprint.endDate)
    elapsedDays = daysBetween(sprint.startDate, today())
    
    // 4. 计算预期完成率（假设线性完成）
    expectedRate = (elapsedDays / totalDays) × 100
    
    // 5. 计算完成率比例
    if expectedRate > 0:
        ratio = actualRate / expectedRate
    else:
        ratio = 1.0  // Sprint刚开始
    
    // 6. 计算得分（上限100分）
    score = min(100, ratio × 100)
    
    // 7. 确定健康状态
    status = determineStatus(score)
    
    // 8. 生成描述
    if actualRate >= expectedRate + 10:
        description = "任务完成率超出预期"
    else if actualRate >= expectedRate:
        description = "任务完成率符合预期"
    else if actualRate >= expectedRate - 10:
        description = "完成率略低于预期进度"
    else:
        description = "完成率明显低于预期，需要加速"
    
    return {
        score: score,
        status: status,
        details: {
            totalTasks: totalTasks,
            completedTasks: completedTasks,
            completionRate: actualRate,
            expectedCompletionRate: expectedRate
        },
        description: description
    }
```

### 示例计算

**场景：Sprint第7天评估**

```
输入：
- totalTasks = 20
- completedTasks = 12
- totalDays = 10
- elapsedDays = 7

计算：
1. actualRate = 12 / 20 × 100 = 60%
2. expectedRate = 7 / 10 × 100 = 70%
3. ratio = 60 / 70 = 0.857
4. score = 0.857 × 100 = 85.7

结果：
- 得分：85.7分
- 状态：GOOD
- 描述：任务完成率符合预期
```

---

## 维度3：阻塞任务 (Blocked Tasks)

### 算法原理

评估阻塞任务的数量和持续时间对Sprint的影响。阻塞任务越多、阻塞时间越长，健康度越低。

### 计算公式

```
1. 计算阻塞任务比例
   blockedRate = blockedTasks / totalTasks × 100

2. 计算平均阻塞天数惩罚
   avgBlockedDaysPenalty = avgBlockedDays × 5

3. 计算得分
   score = max(0, (1 - blockedRate/100) × 100 - avgBlockedDaysPenalty)
```

### 评分阈值

| 阻塞率 | 得分范围 | 健康状态 |
|-------|---------|---------|
| < 5% | 90-100 | EXCELLENT |
| 5-10% | 75-90 | GOOD |
| 10-15% | 60-75 | MODERATE |
| 15-25% | 40-60 | WARNING |
| > 25% | 0-40 | CRITICAL |

### 伪代码实现

```pseudocode
function calculateBlockedTasks(tasks):
    // 1. 识别阻塞任务
    // 方式1：检查is_blocked字段（如果有）
    // 方式2：检查标题/描述中的关键词
    blockedTasks = filter(tasks, task => 
        task.isBlocked == true OR
        task.title.toLowerCase().contains("blocked") OR
        task.description.toLowerCase().contains("blocked")
    )
    
    // 2. 计算阻塞率
    totalTasks = tasks.length
    blockedCount = blockedTasks.length
    blockedRate = (blockedCount / totalTasks) × 100
    
    // 3. 计算平均阻塞天数
    avgBlockedDays = 0
    if blockedCount > 0:
        totalBlockedDays = 0
        for task in blockedTasks:
            if task.blockedSince != null:
                blockedDays = daysBetween(task.blockedSince, today())
                totalBlockedDays += blockedDays
        avgBlockedDays = totalBlockedDays / blockedCount
    
    // 4. 计算基础得分（基于阻塞率）
    baseScore = (1 - blockedRate / 100) × 100
    
    // 5. 应用阻塞天数惩罚（每天扣5分）
    daysPenalty = avgBlockedDays × 5
    score = max(0, baseScore - daysPenalty)
    
    // 6. 确定健康状态
    status = determineStatus(score)
    
    // 7. 生成描述
    if blockedRate < 5:
        description = "阻塞任务极少，进展顺利"
    else if blockedRate < 10:
        description = "阻塞任务数量在可控范围内"
    else if blockedRate < 15:
        description = "存在一定数量的阻塞任务"
    else:
        description = "阻塞任务较多，严重影响进度"
    
    return {
        score: score,
        status: status,
        details: {
            totalTasks: totalTasks,
            blockedTasks: blockedCount,
            blockedRate: blockedRate,
            avgBlockedDays: avgBlockedDays
        },
        description: description
    }
```

### 示例计算

**场景：20个任务，3个阻塞**

```
输入：
- totalTasks = 20
- blockedTasks = 3
- avgBlockedDays = 2.5

计算：
1. blockedRate = 3 / 20 × 100 = 15%
2. baseScore = (1 - 0.15) × 100 = 85
3. daysPenalty = 2.5 × 5 = 12.5
4. score = max(0, 85 - 12.5) = 72.5

结果：
- 得分：72.5分
- 状态：MODERATE
- 描述：存在一定数量的阻塞任务
```

---

## 维度4：工作日志完整性 (Worklog Completeness)

### 算法原理

评估团队成员工作日志记录的完整程度。完整的工作日志是准确追踪进度的基础。

### 计算公式

```
1. 计算预期工作日志天数
   expectedWorkdays = teamSize × workdaysInSprint

2. 统计实际工作日志天数
   actualWorklogDays = distinct(userId + date) 的数量

3. 计算完整性比例
   completenessRate = actualWorklogDays / expectedWorkdays × 100

4. 计算得分
   score = min(100, completenessRate)
```

### 评分阈值

| 完整性 | 得分范围 | 健康状态 |
|-------|---------|---------|
| > 90% | 90-100 | EXCELLENT |
| 80-90% | 80-90 | GOOD |
| 70-80% | 70-80 | MODERATE |
| 60-70% | 60-70 | WARNING |
| < 60% | 0-60 | CRITICAL |

### 伪代码实现

```pseudocode
function calculateWorklogCompleteness(sprint, workLogs):
    // 1. 确定团队规模
    uniqueUsers = distinct(workLogs, wl => wl.userId)
    teamSize = uniqueUsers.length
    
    if teamSize == 0:
        teamSize = 5  // 默认团队规模
    
    // 2. 计算Sprint已过工作日数
    endDate = min(today(), sprint.endDate)
    totalElapsedDays = daysBetween(sprint.startDate, endDate)
    
    // 排除周末（可选）
    workdaysInSprint = countWorkdays(sprint.startDate, endDate)
    
    // 3. 计算预期工作日志天数
    expectedWorkdays = teamSize × workdaysInSprint
    
    // 4. 统计实际工作日志天数
    // 去重：同一天同一人只计算一次
    worklogSet = new Set()
    for worklog in workLogs:
        key = worklog.userId + "_" + worklog.date
        worklogSet.add(key)
    actualWorklogDays = worklogSet.size
    
    // 5. 计算完整性比例
    if expectedWorkdays > 0:
        completenessRate = (actualWorklogDays / expectedWorkdays) × 100
    else:
        completenessRate = 100
    
    // 6. 计算得分（上限100）
    score = min(100, completenessRate)
    
    // 7. 确定健康状态
    status = determineStatus(score)
    
    // 8. 生成描述
    if completenessRate > 90:
        description = "工作日志记录完整"
    else if completenessRate > 80:
        description = "工作日志记录良好"
    else if completenessRate > 70:
        description = "工作日志记录需要改善"
    else:
        description = "工作日志记录不够完整，影响数据准确性"
    
    return {
        score: score,
        status: status,
        details: {
            expectedWorkdays: expectedWorkdays,
            actualWorklogDays: actualWorklogDays,
            completenessRate: completenessRate,
            missingDays: expectedWorkdays - actualWorklogDays
        },
        description: description
    }

// 辅助函数：计算工作日数量（排除周末）
function countWorkdays(startDate, endDate):
    count = 0
    currentDate = startDate
    while currentDate <= endDate:
        if !isWeekend(currentDate):
            count++
        currentDate = addDays(currentDate, 1)
    return count
```

### 示例计算

**场景：5人团队，10个工作日**

```
输入：
- teamSize = 5
- workdaysInSprint = 10
- actualWorklogDays = 42

计算：
1. expectedWorkdays = 5 × 10 = 50
2. completenessRate = 42 / 50 × 100 = 84%
3. score = min(100, 84) = 84

结果：
- 得分：84分
- 状态：GOOD
- 描述：工作日志记录良好
- 缺失天数：8天
```

---

## 维度5：故事点估算准确度 (Story Point Accuracy)

### 算法原理

评估团队故事点估算的准确性，通过对比计划故事点与实际完成的故事点。

### 计算公式

```
1. 统计计划故事点
   plannedPoints = sum(所有任务的storyPoints)

2. 统计已完成故事点
   completedPoints = sum(status==DONE的任务的storyPoints)

3. 预估最终完成故事点
   estimatedCompletePoints = completedPoints / (elapsedDays / totalDays)

4. 计算准确度
   accuracyRate = min(estimatedCompletePoints, plannedPoints) / plannedPoints × 100

5. 计算得分
   score = min(100, accuracyRate)
```

### 评分阈值

| 准确度 | 得分范围 | 健康状态 |
|-------|---------|---------|
| > 95% | 90-100 | EXCELLENT |
| 90-95% | 75-90 | GOOD |
| 80-90% | 60-75 | MODERATE |
| 70-80% | 40-60 | WARNING |
| < 70% | 0-40 | CRITICAL |

### 伪代码实现

```pseudocode
function calculateStoryPointAccuracy(sprint, tasks):
    // 1. 统计计划总故事点
    plannedPoints = sum(tasks, task => task.storyPoints)
    
    // 2. 统计已完成故事点
    completedTasks = filter(tasks, task => task.status == "DONE")
    completedPoints = sum(completedTasks, task => task.storyPoints)
    
    // 3. 计算时间进度
    totalDays = daysBetween(sprint.startDate, sprint.endDate)
    elapsedDays = daysBetween(sprint.startDate, today())
    
    // 4. 预估Sprint结束时完成的故事点
    if elapsedDays > 0:
        progressRatio = elapsedDays / totalDays
        estimatedCompletePoints = completedPoints / progressRatio
    else:
        estimatedCompletePoints = plannedPoints
    
    // 5. 计算准确度
    // 防止超出100%（超额完成不加分）
    actualCompletePoints = min(estimatedCompletePoints, plannedPoints)
    
    if plannedPoints > 0:
        accuracyRate = (actualCompletePoints / plannedPoints) × 100
    else:
        accuracyRate = 100
    
    // 6. 计算平均偏差
    avgDeviation = abs(plannedPoints - estimatedCompletePoints)
    
    // 7. 计算得分
    score = min(100, accuracyRate)
    
    // 8. 确定健康状态
    status = determineStatus(score)
    
    // 9. 生成描述
    if accuracyRate > 95:
        description = "故事点估算非常准确"
    else if accuracyRate > 90:
        description = "故事点估算较为准确"
    else if accuracyRate > 80:
        description = "故事点估算准确度一般"
    else:
        description = "故事点估算偏差较大"
    
    return {
        score: score,
        status: status,
        details: {
            plannedPoints: plannedPoints,
            completedPoints: completedPoints,
            estimatedCompletePoints: estimatedCompletePoints,
            accuracyRate: accuracyRate,
            avgPointDeviation: avgDeviation
        },
        description: description
    }
```

### 示例计算

**场景：计划100点，第7天完成70点**

```
输入：
- plannedPoints = 100
- completedPoints = 70
- totalDays = 10
- elapsedDays = 7

计算：
1. progressRatio = 7 / 10 = 0.7
2. estimatedCompletePoints = 70 / 0.7 = 100
3. actualCompletePoints = min(100, 100) = 100
4. accuracyRate = 100 / 100 × 100 = 100%
5. score = 100

结果：
- 得分：100分
- 状态：EXCELLENT
- 描述：故事点估算非常准确
```

---

## 维度6：团队速度稳定性 (Velocity Stability)

### 算法原理

通过分析最近几个Sprint的速度（完成的故事点）的波动情况，评估团队表现的稳定性。使用变异系数（CV）衡量波动程度。

### 计算公式

```
1. 收集历史速度数据
   velocities = [Sprint1完成点数, Sprint2完成点数, ...]

2. 计算平均速度
   avgVelocity = sum(velocities) / count(velocities)

3. 计算标准差
   variance = sum((v - avgVelocity)² for v in velocities) / count(velocities)
   stdDev = sqrt(variance)

4. 计算变异系数（CV）
   CV = (stdDev / avgVelocity) × 100

5. 计算得分
   score = max(0, 100 - CV × 2)
```

### 评分阈值

| 变异系数 | 得分范围 | 健康状态 |
|---------|---------|---------|
| < 10% | 90-100 | EXCELLENT |
| 10-15% | 75-90 | GOOD |
| 15-25% | 60-75 | MODERATE |
| 25-35% | 40-60 | WARNING |
| > 35% | 0-40 | CRITICAL |

### 伪代码实现

```pseudocode
function calculateVelocityStability(sprint, currentSprintTasks):
    // 1. 获取最近完成的Sprint（最多5个）
    completedSprints = getRecentCompletedSprints(
        projectId: sprint.projectId,
        maxCount: 5
    )
    
    // 2. 检查历史数据是否充足
    if completedSprints.length < 2:
        return {
            score: 75.0,
            status: "GOOD",
            description: "历史数据不足，无法评估",
            details: {}
        }
    
    // 3. 收集历史速度数据
    velocities = []
    for pastSprint in completedSprints:
        pastTasks = getTasksBySprintId(pastSprint.id)
        completedTasks = filter(pastTasks, task => task.status == "DONE")
        velocity = sum(completedTasks, task => task.storyPoints)
        velocities.append(velocity)
    
    // 4. 计算平均速度
    avgVelocity = sum(velocities) / velocities.length
    
    // 5. 计算方差和标准差
    variance = 0
    for velocity in velocities:
        variance += (velocity - avgVelocity)²
    variance = variance / velocities.length
    stdDev = sqrt(variance)
    
    // 6. 计算变异系数（CV）
    if avgVelocity > 0:
        cv = (stdDev / avgVelocity) × 100
    else:
        cv = 0
    
    // 7. 计算得分
    score = max(0, 100 - cv × 2)
    
    // 8. 计算当前Sprint速度
    currentCompletedTasks = filter(currentSprintTasks, task => task.status == "DONE")
    currentVelocity = sum(currentCompletedTasks, task => task.storyPoints)
    
    // 9. 确定健康状态
    status = determineStatus(score)
    
    // 10. 生成描述
    if cv < 10:
        description = "团队速度非常稳定"
    else if cv < 15:
        description = "团队速度保持稳定"
    else if cv < 25:
        description = "团队速度波动适中"
    else:
        description = "团队速度波动较大"
    
    return {
        score: score,
        status: status,
        details: {
            currentVelocity: currentVelocity,
            averageVelocity: avgVelocity,
            velocityVariance: variance,
            stabilityScore: score,
            coefficientOfVariation: cv
        },
        description: description
    }
```

### 示例计算

**场景：最近5个Sprint的速度**

```
输入：
velocities = [65, 70, 68, 72, 67]

计算：
1. avgVelocity = (65+70+68+72+67) / 5 = 68.4
2. variance = [(65-68.4)², (70-68.4)², ...] / 5 = 6.64
3. stdDev = sqrt(6.64) = 2.58
4. cv = (2.58 / 68.4) × 100 = 3.77%
5. score = 100 - 3.77 × 2 = 92.46

结果：
- 得分：92.5分
- 状态：EXCELLENT
- 描述：团队速度非常稳定
- 变异系数：3.77%
```

---

## 总体健康度计算

### 加权平均算法

```pseudocode
function calculateOverallHealth(dimensions):
    // 1. 定义各维度权重
    weights = {
        burndownTrend: 0.25,
        taskCompletion: 0.20,
        blockedTasks: 0.15,
        worklogCompleteness: 0.15,
        storyPointAccuracy: 0.15,
        velocityStability: 0.10
    }
    
    // 2. 计算加权总分
    totalScore = 0
    for dimension in dimensions:
        weight = weights[dimension.name]
        totalScore += dimension.score × weight
    
    // 3. 确定健康等级
    if totalScore >= 90:
        level = "EXCELLENT"
    else if totalScore >= 75:
        level = "GOOD"
    else if totalScore >= 60:
        level = "MODERATE"
    else if totalScore >= 40:
        level = "WARNING"
    else:
        level = "CRITICAL"
    
    // 4. 确定趋势
    previousScore = getPreviousSprintScore()
    if totalScore > previousScore + 5:
        trend = "IMPROVING"
    else if totalScore < previousScore - 5:
        trend = "DECLINING"
    else:
        trend = "STABLE"
    
    return {
        score: totalScore,
        level: level,
        trend: trend
    }
```

### 完整示例

**场景：综合评估**

```
各维度得分：
- burndownTrend: 80分 (权重25%)
- taskCompletion: 85分 (权重20%)
- blockedTasks: 92分 (权重15%)
- worklogCompleteness: 65分 (权重15%)
- storyPointAccuracy: 78分 (权重15%)
- velocityStability: 88分 (权重10%)

计算：
totalScore = 80×0.25 + 85×0.20 + 92×0.15 + 65×0.15 + 78×0.15 + 88×0.10
          = 20 + 17 + 13.8 + 9.75 + 11.7 + 8.8
          = 81.05

结果：
- 总分：81.05分
- 等级：GOOD
- 趋势：IMPROVING（假设上次75分）
```

---

## 辅助函数

### 状态判定函数

```pseudocode
function determineStatus(score):
    if score >= 90:
        return "EXCELLENT"
    else if score >= 75:
        return "GOOD"
    else if score >= 60:
        return "MODERATE"
    else if score >= 40:
        return "WARNING"
    else:
        return "CRITICAL"
```

### 日期计算函数

```pseudocode
// 计算两个日期之间的天数
function daysBetween(startDate, endDate):
    return (endDate - startDate).days

// 判断是否为周末
function isWeekend(date):
    dayOfWeek = date.getDayOfWeek()
    return dayOfWeek == SATURDAY OR dayOfWeek == SUNDAY

// 添加天数
function addDays(date, days):
    return date + Duration(days=days)
```

---

## 算法优化建议

### 1. 性能优化

```pseudocode
// 批量查询优化
function optimizedDataFetch(sprintId):
    // 一次性获取所有需要的数据
    data = parallelFetch({
        sprint: getSprintById(sprintId),
        tasks: getTasksBySprintId(sprintId),
        workLogs: getWorkLogsBySprintId(sprintId),
        burndownPoints: getBurndownPointsBySprintId(sprintId),
        historicalSprints: getHistoricalSprints(projectId, limit=5)
    })
    return data
```

### 2. 缓存策略

```pseudocode
function assessSprintHealthWithCache(sprintId):
    cacheKey = "sprint:health:" + sprintId
    
    // 检查缓存
    cachedResult = cache.get(cacheKey)
    if cachedResult != null AND !cacheExpired(cachedResult):
        return cachedResult
    
    // 计算健康度
    result = assessSprintHealth(sprintId)
    
    // 存入缓存（1小时）
    cache.set(cacheKey, result, ttl=3600)
    
    return result
```

### 3. 异步计算

```pseudocode
function asyncHealthAssessment(sprintId):
    // 并行计算各维度
    results = await Promise.all([
        calculateBurndownTrend(sprint, tasks, burndownPoints),
        calculateTaskCompletion(sprint, tasks),
        calculateBlockedTasks(tasks),
        calculateWorklogCompleteness(sprint, workLogs),
        calculateStoryPointAccuracy(sprint, tasks),
        calculateVelocityStability(sprint, tasks)
    ])
    
    return aggregateResults(results)
```

---

## 算法调优参数

### 可配置参数表

| 参数名 | 默认值 | 说明 | 建议范围 |
|-------|-------|------|---------|
| BURNDOWN_AHEAD_PENALTY | 2.0 | 提前完成惩罚因子 | 1.5-3.0 |
| BURNDOWN_BEHIND_PENALTY | 1.5 | 落后惩罚因子 | 1.0-2.0 |
| BLOCKED_DAYS_PENALTY | 5 | 每天阻塞扣分 | 3-10 |
| VELOCITY_HISTORY_COUNT | 5 | 参考历史Sprint数 | 3-10 |
| CV_SCORE_FACTOR | 2.0 | 变异系数得分因子 | 1.5-3.0 |
| HEALTH_THRESHOLD_EXCELLENT | 90 | 优秀阈值 | 85-95 |
| HEALTH_THRESHOLD_GOOD | 75 | 良好阈值 | 70-80 |
| HEALTH_THRESHOLD_MODERATE | 60 | 一般阈值 | 55-65 |
| HEALTH_THRESHOLD_WARNING | 40 | 警告阈值 | 35-45 |

---

## 总结

本文档详细说明了Sprint健康度评估系统的六个核心维度算法：

1. **燃尽趋势** - 基于偏差百分比和惩罚因子
2. **任务完成率** - 实际与预期的比例计算
3. **阻塞任务** - 比例和持续时间双重评估
4. **工作日志完整性** - 预期与实际天数对比
5. **故事点准确度** - 估算与实际偏差分析
6. **速度稳定性** - 变异系数统计方法

每个算法都包含：
- ✅ 原理说明
- ✅ 计算公式
- ✅ 评分阈值
- ✅ 伪代码实现
- ✅ 实际示例

这些算法共同构成了一个全面、客观、可量化的Sprint健康评估体系。

**持续监控，一步步显示。**

---

*文档版本：v1.0.0*  
*最后更新：2024-01-15*
