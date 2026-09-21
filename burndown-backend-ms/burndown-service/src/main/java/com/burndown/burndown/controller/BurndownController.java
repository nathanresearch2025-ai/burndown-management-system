package com.burndown.burndown.controller;

import com.burndown.burndown.entity.BurndownPoint;
import com.burndown.burndown.service.BurndownService;
import com.burndown.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/burndown")
@RequiredArgsConstructor
public class BurndownController {

    private final BurndownService burndownService;

    @GetMapping("/sprint/{sprintId}")
    public ResponseEntity<ApiResponse<List<BurndownPoint>>> getBySprintId(@PathVariable Long sprintId) {
        return ResponseEntity.ok(ApiResponse.ok(burndownService.getBySprintId(sprintId)));
    }

    @PostMapping("/sprint/{sprintId}/record")
    public ResponseEntity<ApiResponse<BurndownPoint>> recordDailyPoint(@PathVariable Long sprintId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(burndownService.recordDailyPoint(sprintId)));
    }
}
