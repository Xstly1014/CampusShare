package com.campushare.agent.controller;

import com.campushare.agent.dto.CategoryCreateRequest;
import com.campushare.agent.dto.CategoryRenameRequest;
import com.campushare.agent.dto.CategoryResponse;
import com.campushare.agent.service.AgentSessionCategoryService;
import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/agent/categories")
@RequiredArgsConstructor
public class AgentCategoryController {

    private final AgentSessionCategoryService categoryService;
    private final JwtUtils jwtUtils;

    @PostMapping
    public Mono<Result<CategoryResponse>> create(
            @RequestHeader("Authorization") String token,
            @RequestBody(required = false) CategoryCreateRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromCallable(() -> categoryService.createCategory(userId,
                        request != null ? request : new CategoryCreateRequest()))
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping
    public Mono<Result<List<CategoryResponse>>> list(
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromCallable(() -> categoryService.getUserCategories(userId))
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/{categoryId}")
    public Mono<Result<CategoryResponse>> rename(
            @RequestHeader("Authorization") String token,
            @PathVariable String categoryId,
            @RequestBody CategoryRenameRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromCallable(() -> categoryService.renameCategory(userId, categoryId, request))
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{categoryId}")
    public Mono<Result<Void>> delete(
            @RequestHeader("Authorization") String token,
            @PathVariable String categoryId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromRunnable(() -> categoryService.deleteCategory(userId, categoryId))
                .thenReturn(Result.<Void>success(null))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
