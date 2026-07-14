package com.campushare.agent.controller;

import com.campushare.agent.entity.BadCase;
import com.campushare.agent.service.BadCaseService;
import com.campushare.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/agent/bad-cases")
@RequiredArgsConstructor
public class BadCaseController {

    private final BadCaseService badCaseService;

    @GetMapping
    public Result<List<BadCase>> list(@RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "20") int limit) {
        List<BadCase> badCases;
        if (status != null && !status.isEmpty()) {
            badCases = badCaseService.findByStatus(status);
        } else {
            badCases = badCaseService.findNewCases(limit);
        }
        return Result.success(badCases);
    }

    @GetMapping("/{id}")
    public Result<BadCase> getById(@PathVariable String id) {
        BadCase badCase = badCaseService.getById(id);
        if (badCase == null) {
            return Result.error(404, "Bad case not found");
        }
        return Result.success(badCase);
    }

    @PutMapping("/{id}/status")
    public Result<BadCase> updateStatus(@PathVariable String id,
                                         @RequestParam String status,
                                         @RequestParam(required = false) String note) {
        BadCase badCase = badCaseService.updateStatus(id, status, note);
        return Result.success(badCase);
    }

    @PutMapping("/{id}/assign")
    public Result<BadCase> assign(@PathVariable String id, @RequestParam String assignee) {
        BadCase badCase = badCaseService.assign(id, assignee);
        return Result.success(badCase);
    }

    @PutMapping("/{id}/note")
    public Result<BadCase> addNote(@PathVariable String id, @RequestBody Map<String, String> body) {
        String note = body.get("note");
        BadCase badCase = badCaseService.addNote(id, note);
        return Result.success(badCase);
    }

    @PostMapping("/{id}/convert-to-test-case")
    public Result<Void> convertToTestCase(@PathVariable String id) {
        badCaseService.convertToTestCase(id);
        return Result.success(null);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = badCaseService.getOverallStats();
        return Result.success(stats);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        badCaseService.delete(id);
        return Result.success(null);
    }

    @PostMapping("/collect")
    public Result<Void> collect() {
        badCaseService.autoCollectBadCases();
        return Result.success(null);
    }
}