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
    public Result<List<Map<String, Object>>> getAllStatus() {
        return Result.success(sloService.getAllSloStatus());
    }

    @GetMapping("/status/{objective}")
    public Result<Map<String, Object>> getStatus(@PathVariable String objective) {
        return Result.success(sloService.getSloStatus(objective));
    }

    @GetMapping("/summary/{objective}")
    public Result<Map<String, Object>> getSummary(@PathVariable String objective) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(5);
        return Result.success(sloService.getSloSummary(objective, startTime, endTime));
    }

    @GetMapping("/latency/{objective}")
    public Result<Map<String, Object>> getLatencyPercentiles(@PathVariable String objective,
                                                              @RequestParam(defaultValue = "5") int minutes) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(minutes);
        return Result.success(sloService.getLatencyPercentiles(objective, startTime, endTime));
    }

    @GetMapping("/burn-rate/{objective}")
    public Result<Map<String, Object>> checkBurnRateAlerts(@PathVariable String objective) {
        return Result.success(sloService.checkBurnRateAlerts(objective));
    }

    @GetMapping("/alerts/{objective}")
    public Result<List<Map<String, Object>>> getRecentAlerts(@PathVariable String objective,
                                                              @RequestParam(defaultValue = "20") int limit) {
        return Result.success(sloService.getRecentAlerts(objective, limit));
    }

    @GetMapping("/error-rate/{objective}")
    public Result<Double> getErrorRate(@PathVariable String objective,
                                        @RequestParam(defaultValue = "5") int minutes) {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusMinutes(minutes);
        return Result.success(sloService.getErrorRate(objective, startTime, endTime));
    }
}