package com.campushare.agent.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campushare.agent.entity.PromptVersion;
import com.campushare.agent.mapper.PromptVersionMapper;
import com.campushare.agent.prompt.PromptVersionManager;
import com.campushare.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * System Prompt 版本管理 API（管理员操作）。
 *
 * 路径：/agent/prompt-versions
 * 鉴权：X-Internal-Token（app.internal.token），非用户 JWT。
 * 原因：版本切换/灰度/回滚属管理员操作，不应暴露给终端用户。
 *
 * 端点：
 * - GET /current 查询当前生效版本
 * - GET /list 查询所有版本
 * - POST /switch 切换到指定版本
 * - POST /rollback 回滚到上一版本
 * - POST /gray-ratio 设置灰度比例
 */
@Slf4j
@RestController
@RequestMapping("/agent/prompt-versions")
@RequiredArgsConstructor
public class PromptVersionController {

    private final PromptVersionManager versionManager;
    private final PromptVersionMapper versionMapper;

    @Value("${app.internal.token:campushare-internal-token}")
    private String internalToken;

    @GetMapping("/current")
    public Mono<Result<Map<String, Object>>> getCurrent(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        if (!internalToken.equals(token)) {
            return Mono.just(Result.<Map<String, Object>>error(4030, "无权操作：需要内部 token"));
        }

        return Mono.fromCallable(() -> {
            PromptVersion version = versionManager.getCurrentVersion(userId);
            Map<String, Object> data = toSummary(version);
            return data;
        })
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @GetMapping("/list")
    public Mono<Result<List<Map<String, Object>>>> list(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {

        if (!internalToken.equals(token)) {
            return Mono.just(Result.<List<Map<String, Object>>>error(4030, "无权操作：需要内部 token"));
        }

        return Mono.fromCallable(() -> {
            LambdaQueryWrapper<PromptVersion> wrapper = new LambdaQueryWrapper<>();
            wrapper.orderByDesc(PromptVersion::getCreatedAt);
            List<PromptVersion> versions = versionMapper.selectList(wrapper);
            return versions.stream().map(this::toSummary).toList();
        })
                .subscribeOn(Schedulers.boundedElastic())
                .map(Result::success);
    }

    @PostMapping("/switch")
    public Mono<Result<Void>> switchVersion(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestBody Map<String, String> body) {

        if (!internalToken.equals(token)) {
            return Mono.just(Result.<Void>error(4030, "无权操作：需要内部 token"));
        }

        String newVersion = body.get("version");
        if (newVersion == null || newVersion.isBlank()) {
            return Mono.just(Result.<Void>error(4000, "缺少 version 参数"));
        }

        return Mono.fromRunnable(() -> versionManager.switchVersion(newVersion))
                .thenReturn(Result.<Void>success(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/rollback")
    public Mono<Result<Void>> rollback(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {

        if (!internalToken.equals(token)) {
            return Mono.just(Result.<Void>error(4030, "无权操作：需要内部 token"));
        }

        return Mono.fromRunnable(versionManager::rollback)
                .thenReturn(Result.<Void>success(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/gray-ratio")
    public Mono<Result<Void>> setGrayRatio(
            @RequestHeader(value = "X-Internal-Token", required = false) String token,
            @RequestParam int ratio) {

        if (!internalToken.equals(token)) {
            return Mono.just(Result.<Void>error(4030, "无权操作：需要内部 token"));
        }

        return Mono.fromRunnable(() -> versionManager.setGrayRatio(ratio))
                .thenReturn(Result.<Void>success(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 转为摘要 Map（不含完整 Prompt 内容，避免响应过大）。
     */
    private Map<String, Object> toSummary(PromptVersion v) {
        Map<String, Object> data = new HashMap<>();
        data.put("id", v.getId());
        data.put("version", v.getVersion());
        data.put("changelog", v.getChangelog());
        data.put("status", v.getStatus());
        data.put("grayRatio", v.getGrayRatio());
        data.put("creator", v.getCreator());
        data.put("createdAt", v.getCreatedAt());
        data.put("releasedAt", v.getReleasedAt());
        data.put("platformPromptLength", v.getPlatformPrompt() != null ? v.getPlatformPrompt().length() : 0);
        data.put("guardrailPromptLength", v.getGuardrailPrompt() != null ? v.getGuardrailPrompt().length() : 0);
        return data;
    }
}
