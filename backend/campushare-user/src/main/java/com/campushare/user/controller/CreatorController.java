package com.campushare.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campushare.common.exception.BusinessException;
import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.CreatorApplicationItem;
import com.campushare.user.dto.CreatorApplyRequest;
import com.campushare.user.dto.CreatorStatsDTO;
import com.campushare.user.dto.CreatorStatusDTO;
import com.campushare.user.dto.CreatorVerifyRequest;
import com.campushare.user.service.CreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/creator")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorService creatorService;
    private final JwtUtils jwtUtils;

    @GetMapping("/stats")
    public Result<CreatorStatsDTO> getStats(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(creatorService.getStats(userId));
    }

    @GetMapping("/status")
    public Result<CreatorStatusDTO> getStatus(@RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Result.success(creatorService.getStatus(userId));
    }

    @PostMapping("/apply")
    public Result<Void> apply(
            @RequestHeader("Authorization") String token,
            @RequestBody CreatorApplyRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        creatorService.apply(userId, request);
        return Result.success("申请已提交，请等待审核", null);
    }

    @GetMapping("/admin/applications")
    public Result<IPage<CreatorApplicationItem>> getApplications(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        if (!creatorService.isAdmin(userId)) {
            throw new BusinessException(4030, "无权限访问");
        }
        return Result.success(creatorService.getApplicationList(status, page, size));
    }

    @PostMapping("/admin/applications/{id}/verify")
    public Result<Void> verify(
            @RequestHeader("Authorization") String token,
            @PathVariable Integer id,
            @RequestBody CreatorVerifyRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        creatorService.verify(userId, id, request);
        return Result.success(request.isApproved() ? "已通过认证" : "已驳回申请", null);
    }
}
