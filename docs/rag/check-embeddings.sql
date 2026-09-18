-- 检查任务是否有向量
SELECT
    task_key,
    title,
    status,
    assignee_id,
    CASE WHEN embedding IS NOT NULL THEN 'Yes' ELSE 'No' END as has_embedding,
    CASE WHEN embedding IS NOT NULL THEN array_length(string_to_array(embedding::text, ','), 1) ELSE 0 END as vector_dimension
FROM tasks
WHERE project_id = 1
ORDER BY task_key;
