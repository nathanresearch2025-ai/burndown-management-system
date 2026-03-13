package com.burndown.repository;

import com.burndown.entity.Task;
import com.pgvector.PGvector;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findBySprintId(Long sprintId);
    List<Task> findByProjectId(Long projectId);
    List<Task> findByAssigneeId(Long assigneeId);
    List<Task> findByProjectIdOrderByUpdatedAtDesc(Long projectId, Pageable pageable);

    @Query(value = "SELECT MAX(CAST(SUBSTRING(task_key FROM '[0-9]+$') AS INTEGER)) FROM tasks WHERE project_id = ?1", nativeQuery = true)
    Integer findMaxTaskNumberByProjectId(Long projectId);

    /**
     * Find similar tasks using vector cosine similarity
     * Returns tasks ordered by similarity (most similar first)
     * Excludes the task with the given title to avoid self-reference
     */
    @Query(value = """
        SELECT t.* FROM tasks t
        WHERE t.project_id = :projectId
        AND t.embedding IS NOT NULL
        AND LOWER(t.title) != LOWER(:excludeTitle)
        ORDER BY t.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<Task> findSimilarTasksByEmbedding(
            @Param("projectId") Long projectId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("excludeTitle") String excludeTitle,
            @Param("limit") int limit
    );

    /**
     * Find tasks without embeddings (for batch processing)
     */
    @Query("SELECT t FROM Task t WHERE t.embedding IS NULL")
    List<Task> findTasksWithoutEmbeddings(Pageable pageable);
}
