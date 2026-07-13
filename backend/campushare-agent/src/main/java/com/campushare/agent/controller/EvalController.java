package com.campushare.agent.controller;

import com.campushare.agent.entity.EvalResult;
import com.campushare.agent.entity.EvalTestCase;
import com.campushare.agent.service.EvalService;
import com.campushare.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    @PostMapping("/test-cases")
    public Result<EvalTestCase> createTestCase(@RequestBody EvalTestCase testCase) {
        return Result.success(evalService.createTestCase(testCase));
    }

    @PutMapping("/test-cases/{id}")
    public Result<EvalTestCase> updateTestCase(@PathVariable String id, @RequestBody EvalTestCase testCase) {
        return Result.success(evalService.updateTestCase(id, testCase));
    }

    @DeleteMapping("/test-cases/{id}")
    public Result<Void> deleteTestCase(@PathVariable String id) {
        evalService.deleteTestCase(id);
        return Result.success(null);
    }

    @GetMapping("/test-cases/{id}")
    public Result<EvalTestCase> getTestCase(@PathVariable String id) {
        return Result.success(evalService.getTestCase(id));
    }

    @GetMapping("/test-cases")
    public Result<List<EvalTestCase>> listTestCases(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isGolden,
            @RequestParam(required = false) Integer priority) {
        return Result.success(evalService.listTestCases(category, isGolden, priority));
    }

    @GetMapping("/test-cases/golden")
    public Result<List<EvalTestCase>> getGoldenCases() {
        return Result.success(evalService.getGoldenCases());
    }

    @PostMapping("/run")
    public Result<Map<String, String>> runEval(@RequestBody List<String> testCaseIds) {
        String runId = evalService.runEval(testCaseIds, "eval-system");
        return Result.success(Map.of("runId", runId));
    }

    @PostMapping("/run/golden")
    public Result<Map<String, String>> runGoldenEval() {
        String runId = evalService.runGoldenEval("eval-system");
        return Result.success(Map.of("runId", runId));
    }

    @GetMapping("/runs/{runId}")
    public Result<List<EvalResult>> getRunResults(@PathVariable String runId) {
        return Result.success(evalService.getRunResults(runId));
    }

    @GetMapping("/runs/{runId}/failed")
    public Result<List<EvalResult>> getFailedResults(@PathVariable String runId) {
        return Result.success(evalService.getFailedResults(runId));
    }

    @GetMapping("/runs/{runId}/summary")
    public Result<Map<String, Object>> getRunSummary(@PathVariable String runId) {
        return Result.success(evalService.getRunSummary(runId));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getOverallStats(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return Result.success(evalService.getOverallStats(
                startTime != null ? startTime : LocalDateTime.now().minusDays(7),
                endTime != null ? endTime : LocalDateTime.now()));
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> getTrendByDay(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return Result.success(evalService.getTrendByDay(
                startTime != null ? startTime : LocalDateTime.now().minusDays(30),
                endTime != null ? endTime : LocalDateTime.now()));
    }
}