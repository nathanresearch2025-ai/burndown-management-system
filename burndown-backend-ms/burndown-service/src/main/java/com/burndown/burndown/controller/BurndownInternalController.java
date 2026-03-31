package com.burndown.burndown.controller;

import com.burndown.common.dto.ApiResponse;
import com.burndown.burndown.service.BurndownService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/burndown")
@RequiredArgsConstructor
public class BurndownInternalController {

    private final BurndownService burndownService;

    @DeleteMapping("/sprint/{sprintId}")
    public ResponseEntity<ApiResponse<Void>> deleteSprintPoints(@PathVariable Long sprintId) {
        burndownService.deleteSprintPoints(sprintId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted burndown points for sprint " + sprintId));
    }
}
