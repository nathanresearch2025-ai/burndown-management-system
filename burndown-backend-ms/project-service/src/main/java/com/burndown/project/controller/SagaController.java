package com.burndown.project.controller;

import com.burndown.common.dto.ApiResponse;
import com.burndown.common.exception.ResourceNotFoundException;
import com.burndown.project.dto.SagaInstanceDTO;
import com.burndown.project.entity.SagaInstance;
import com.burndown.project.entity.SagaStepLog;
import com.burndown.project.repository.SagaInstanceRepository;
import com.burndown.project.repository.SagaStepLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/sagas")
@RequiredArgsConstructor
public class SagaController {

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaStepLogRepository sagaStepLogRepository;

    @GetMapping("/{sagaId}")
    public ResponseEntity<ApiResponse<SagaInstanceDTO>> getSaga(@PathVariable String sagaId) {
        SagaInstance saga = sagaInstanceRepository.findById(sagaId)
                .orElseThrow(() -> new ResourceNotFoundException("Saga with id " + sagaId + " not found"));
        List<SagaStepLog> logs = sagaStepLogRepository.findBySagaIdOrderByExecutedAtAsc(sagaId);
        return ResponseEntity.ok(ApiResponse.ok(toDTO(saga, logs)));
    }

    private SagaInstanceDTO toDTO(SagaInstance saga, List<SagaStepLog> logs) {
        SagaInstanceDTO dto = new SagaInstanceDTO();
        dto.setId(saga.getId());
        dto.setSagaType(saga.getSagaType());
        dto.setStatus(saga.getStatus());
        dto.setCurrentStep(saga.getCurrentStep());
        dto.setSprintId(saga.getSprintId());
        dto.setProjectId(saga.getProjectId());
        dto.setNextSprintId(saga.getNextSprintId());
        dto.setFailureReason(saga.getFailureReason());
        dto.setCreatedAt(saga.getCreatedAt());
        dto.setUpdatedAt(saga.getUpdatedAt());
        dto.setSteps(logs.stream().map(l -> {
            SagaInstanceDTO.SagaStepLogDTO s = new SagaInstanceDTO.SagaStepLogDTO();
            s.setStepName(l.getStepName());
            s.setStepStatus(l.getStepStatus());
            s.setErrorMsg(l.getErrorMsg());
            s.setExecutedAt(l.getExecutedAt());
            return s;
        }).collect(Collectors.toList()));
        return dto;
    }
}
