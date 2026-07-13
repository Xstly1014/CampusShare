package com.campushare.agent.controller;

import com.campushare.agent.service.SloService;
import com.campushare.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent/slo")
@RequiredArgsConstructor
public class SloController {

    private final SloService sloService;

    @GetMapping("/status")
    public Result<List<Map<String, Object>>> getAllSloStatus() {
        return Result.success(sloService.getAllSloStatus());
    }

    @GetMapping("/status/{objectiveName}")
    public Result<Map<String, Object>> getSloStatus(@PathVariable String objectiveName) {
        return Result.success(sloService.getSloStatus(objectiveName));
    }

    @GetMapping("/summary/{objectiveName}")
    public Result<Map<String, Object>> getSloSummary(
            @PathVariable String objectiveName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return Result.success(sloService.getSloSummary(objectiveName,
                startTime != null ? startTime : LocalDateTime.now().minusHours(1),
                endTime != null ? endTime : LocalDateTime.now()));
    }

    @GetMapping("/breaching")
    public Result<List<Map<String, Object>>> getBreachingObjectives() {
        List<Map<String, Object>> allStatus = sloService.getAllSloStatus();
        allStatus.removeIf(status -> !Boolean.TRUE.equals(status.get("breaching")));
        return Result.success(allStatus);
    }

    @GetMapping("/latency/{objectiveName}")
    public Result<Map<String, Object>> getLatencyPercentiles(
            @PathVariable String objectiveName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return Result.success(sloService.getLatencyPercentiles(objectiveName,
                startTime != null ? startTime : LocalDateTime.now().minusHours(1),
                endTime != null ? endTime : LocalDateTime.now()));
    }

    @GetMapping("/error-rate/{objectiveName}")
    public Result<Map<String, Double>> getErrorRate(
            @PathVariable String objectiveName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        double errorRate = sloService.getErrorRate(objectiveName,
                startTime != null ? startTime : LocalDateTime.now().minusHours(1),
                endTime != null ? endTime : LocalDateTime.now());
        return Result.success(Map.of("errorRate", errorRate));
    }

    @GetMapping("/burn-rate/{objectiveName}")
    public Result<Map<String, Double>> getBurnRate(
            @PathVariable String objectiveName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        double burnRate = sloService.calculateBurnRate(objectiveName,
                startTime != null ? startTime : LocalDateTime.now().minusHours(1),
                endTime != null ? endTime : LocalDateTime.now());
        return Result.success(Map.of("burnRate", burnRate));
    }
}