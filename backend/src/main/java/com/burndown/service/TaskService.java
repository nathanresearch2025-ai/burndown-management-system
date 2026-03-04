package com.burndown.service;

import com.burndown.dto.CreateTaskRequest;
import com.burndown.entity.Project;
import com.burndown.entity.Task;
import com.burndown.repository.ProjectRepository;
import com.burndown.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Task createTask(CreateTaskRequest request, Long reporterId) {
        String taskKey = generateTaskKey(request.getProjectId());

        Task task = new Task();
        task.setProjectId(request.getProjectId());
        task.setSprintId(request.getSprintId());
        task.setTaskKey(taskKey);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setType(Task.TaskType.valueOf(request.getType()));
        task.setPriority(Task.TaskPriority.valueOf(request.getPriority()));
        task.setStoryPoints(request.getStoryPoints());
        task.setOriginalEstimate(request.getOriginalEstimate());
        task.setRemainingEstimate(request.getOriginalEstimate());
        task.setAssigneeId(request.getAssigneeId());
        task.setReporterId(reporterId);
        task.setStatus(Task.TaskStatus.TODO);

        return taskRepository.save(task);
    }

    private String generateTaskKey(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        Integer maxNumber = taskRepository.findMaxTaskNumberByProjectId(projectId);
        int nextNumber = (maxNumber == null) ? 1 : maxNumber + 1;
        return project.getProjectKey() + "-" + nextNumber;
    }

    public List<Task> getTasksBySprint(Long sprintId) {
        return taskRepository.findBySprintId(sprintId);
    }

    public List<Task> getTasksByProject(Long projectId) {
        return taskRepository.findByProjectId(projectId);
    }

    public Task getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found"));
    }

    @Transactional
    public Task updateTaskStatus(Long taskId, String status) {
        Task task = getTaskById(taskId);
        task.setStatus(Task.TaskStatus.valueOf(status));
        if (status.equals("DONE")) {
            task.setResolvedAt(LocalDateTime.now());
        }
        return taskRepository.save(task);
    }

    @Transactional
    public Task updateTask(Long taskId, CreateTaskRequest request) {
        Task task = getTaskById(taskId);
        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setType(Task.TaskType.valueOf(request.getType()));
        if (request.getPriority() != null) {
            task.setPriority(Task.TaskPriority.valueOf(request.getPriority()));
        }
        task.setStoryPoints(request.getStoryPoints());
        task.setOriginalEstimate(request.getOriginalEstimate());
        task.setAssigneeId(request.getAssigneeId());
        return taskRepository.save(task);
    }
}
