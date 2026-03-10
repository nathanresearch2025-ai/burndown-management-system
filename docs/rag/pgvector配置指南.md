# PostgreSQL pgvector 扩展设置指南

## 概述

pgvector 是 PostgreSQL 的向量相似度搜索扩展，可以用于实现更精确的任务相似度匹配。当前系统使用基于关键词的相似度算法，未来可以升级为基于向量嵌入的语义相似度搜索。

## 安装 pgvector

### 方法 1: 使用 Docker（推荐）

使用支持 pgvector 的 PostgreSQL Docker 镜像：

```yaml
# docker-compose.yml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: burndown_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: root
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
```

### 方法 2: 手动安装

在现有 PostgreSQL 实例上安装：

```bash
# Ubuntu/Debian
sudo apt-get install postgresql-16-pgvector

# macOS (Homebrew)
brew install pgvector

# 从源码编译
git clone https://github.com/pgvector/pgvector.git
cd pgvector
make
make install
```

## 启用扩展

连接到数据库并启用 pgvector：

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 验证安装
SELECT * FROM pg_extension WHERE extname = 'vector';
```

## 数据库 Schema 更新

添加向量存储表：

```sql
-- 创建任务嵌入表
CREATE TABLE task_embeddings (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    title_embedding VECTOR(1536),  -- OpenAI text-embedding-3-small 维度
    description_embedding VECTOR(1536),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(task_id)
);

-- 创建向量索引（IVFFlat）
CREATE INDEX idx_task_embeddings_title_vector
ON task_embeddings
USING ivfflat (title_embedding vector_cosine_ops)
WITH (lists = 100);

CREATE INDEX idx_task_embeddings_description_vector
ON task_embeddings
USING ivfflat (description_embedding vector_cosine_ops)
WITH (lists = 100);

-- 创建常规索引
CREATE INDEX idx_task_embeddings_task_id ON task_embeddings(task_id);
CREATE INDEX idx_task_embeddings_created_at ON task_embeddings(created_at);
```

## 向量索引类型

### IVFFlat（推荐用于中小型数据集）

```sql
CREATE INDEX ON task_embeddings
USING ivfflat (title_embedding vector_cosine_ops)
WITH (lists = 100);
```

- **优点**：查询速度快，内存占用少
- **缺点**：需要训练，精度略低
- **适用**：10,000 - 1,000,000 条记录

### HNSW（推荐用于大型数据集）

```sql
CREATE INDEX ON task_embeddings
USING hnsw (title_embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

- **优点**：查询速度更快，精度更高
- **缺点**：内存占用较大
- **适用**：> 1,000,000 条记录

## 距离度量

pgvector 支持三种距离度量：

1. **余弦距离**（推荐用于文本嵌入）
   ```sql
   SELECT * FROM task_embeddings
   ORDER BY title_embedding <=> '[0.1, 0.2, ...]'
   LIMIT 5;
   ```

2. **欧几里得距离**（L2）
   ```sql
   SELECT * FROM task_embeddings
   ORDER BY title_embedding <-> '[0.1, 0.2, ...]'
   LIMIT 5;
   ```

3. **内积距离**
   ```sql
   SELECT * FROM task_embeddings
   ORDER BY title_embedding <#> '[0.1, 0.2, ...]'
   LIMIT 5;
   ```

## 后端集成示例

### 1. 添加依赖

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.4</version>
</dependency>
```

### 2. 创建实体

```java
@Entity
@Table(name = "task_embeddings")
public class TaskEmbedding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true)
    private Long taskId;

    @Type(type = "com.pgvector.PGvector")
    @Column(name = "title_embedding", columnDefinition = "vector(1536)")
    private float[] titleEmbedding;

    @Type(type = "com.pgvector.PGvector")
    @Column(name = "description_embedding", columnDefinition = "vector(1536)")
    private float[] descriptionEmbedding;

    // getters and setters
}
```

### 3. Repository 查询

```java
public interface TaskEmbeddingRepository extends JpaRepository<TaskEmbedding, Long> {

    @Query(value = "SELECT te.task_id, " +
           "(te.title_embedding <=> CAST(:embedding AS vector)) AS distance " +
           "FROM task_embeddings te " +
           "WHERE te.task_id IN (SELECT id FROM tasks WHERE project_id = :projectId) " +
           "ORDER BY distance " +
           "LIMIT :limit",
           nativeQuery = true)
    List<Object[]> findSimilarByEmbedding(
        @Param("projectId") Long projectId,
        @Param("embedding") String embedding,
        @Param("limit") int limit
    );
}
```

### 4. 嵌入服务

```java
@Service
public class EmbeddingService {

    private final RestTemplate restTemplate;

    @Value("${ai.embedding-url}")
    private String embeddingUrl;

    @Value("${ai.api-key}")
    private String apiKey;

    public float[] generateEmbedding(String text) {
        // 调用 OpenAI Embedding API
        Map<String, Object> request = Map.of(
            "model", "text-embedding-3-small",
            "input", text
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            embeddingUrl,
            entity,
            Map.class
        );

        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        List<Double> embedding = (List<Double>) data.get(0).get("embedding");

        return embedding.stream()
            .mapToDouble(Double::doubleValue)
            .toArray();
    }
}
```

## 性能优化

### 1. 索引参数调优

```sql
-- IVFFlat: lists 参数
-- 推荐值：rows / 1000
-- 例如：100,000 行 → lists = 100

-- HNSW: m 和 ef_construction 参数
-- m: 16-64（越大越精确但越慢）
-- ef_construction: 64-200（构建时的搜索深度）
```

### 2. 查询优化

```sql
-- 设置查询时的探测列表数
SET ivfflat.probes = 10;

-- 设置 HNSW 查询时的搜索深度
SET hnsw.ef_search = 40;
```

### 3. 批量插入

```sql
-- 使用 COPY 或批量 INSERT
COPY task_embeddings (task_id, title_embedding)
FROM '/path/to/embeddings.csv'
WITH (FORMAT csv);
```

## 监控和维护

### 查看索引大小

```sql
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size
FROM pg_stat_user_indexes
WHERE tablename = 'task_embeddings';
```

### 重建索引

```sql
-- 当数据量增长时，重建索引以优化性能
REINDEX INDEX idx_task_embeddings_title_vector;
```

### 查询性能分析

```sql
EXPLAIN ANALYZE
SELECT * FROM task_embeddings
ORDER BY title_embedding <=> '[0.1, 0.2, ...]'
LIMIT 5;
```

## 迁移策略

### 从关键词匹配迁移到向量搜索

1. **并行运行**：同时保留关键词和向量搜索
2. **A/B 测试**：对比两种方法的效果
3. **逐步切换**：根据测试结果逐步切换
4. **降级方案**：向量搜索失败时回退到关键词匹配

```java
public List<Task> findSimilarTasks(String title, Long projectId) {
    try {
        // 尝试使用向量搜索
        float[] embedding = embeddingService.generateEmbedding(title);
        return vectorSearchService.findSimilar(embedding, projectId);
    } catch (Exception e) {
        // 降级到关键词搜索
        log.warn("Vector search failed, falling back to keyword search", e);
        return keywordSearchService.findSimilar(title, projectId);
    }
}
```

## 成本考虑

### Embedding API 成本

- OpenAI text-embedding-3-small: $0.02 / 1M tokens
- 平均任务标题：~10 tokens
- 10,000 个任务：~$0.002

### 存储成本

- 每个向量（1536 维 float32）：6KB
- 10,000 个任务：~60MB
- 索引开销：约 2-3 倍

## 参考资源

- pgvector GitHub: https://github.com/pgvector/pgvector
- pgvector 文档: https://github.com/pgvector/pgvector#readme
- OpenAI Embeddings: https://platform.openai.com/docs/guides/embeddings
- PostgreSQL 向量搜索最佳实践: https://supabase.com/blog/pgvector-vs-pinecone

## 注意事项

1. **向量维度**：确保嵌入维度与表定义一致
2. **索引训练**：IVFFlat 需要足够的数据才能有效（建议 > 1000 行）
3. **内存使用**：HNSW 索引会占用较多内存
4. **更新频率**：频繁更新会影响索引性能
5. **备份恢复**：向量数据较大，注意备份时间

## 当前状态

系统当前使用基于关键词的相似度算法，未启用 pgvector。如需启用向量搜索：

1. 按照本文档安装和配置 pgvector
2. 实现嵌入生成服务
3. 创建任务嵌入表和索引
4. 更新 TaskAiService 使用向量搜索
5. 进行充分测试后上线
