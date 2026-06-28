package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import com.campushare.user.dto.CreatorApplyRequest;
import com.campushare.user.dto.CreatorStatsDTO;
import com.campushare.user.dto.CreatorStatusDTO;
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
}
