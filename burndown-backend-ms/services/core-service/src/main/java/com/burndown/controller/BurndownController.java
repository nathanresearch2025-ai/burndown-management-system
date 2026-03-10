package com.burndown.controller;

import com.burndown.entity.BurndownPoint;
import com.burndown.service.BurndownService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/burndown")
public class BurndownController {

    private final BurndownService burndownService;

    public BurndownController(BurndownService burndownService) {
        this.burndownService = burndownService;
    }

    @GetMapping("/sprints/{sprintId}")
    public ResponseEntity<List<BurndownPoint>> getBurndownData(@PathVariable Long sprintId) {
        List<BurndownPoint> data = burndownService.getBurndownData(sprintId);
        return ResponseEntity.ok(data);
    }

    @PostMapping("/sprints/{sprintId}/calculate")
    public ResponseEntity<Void> calculateBurndown(@PathVariable Long sprintId) {
        burndownService.calculateBurndown(sprintId);
        return ResponseEntity.ok().build();
    }
}
