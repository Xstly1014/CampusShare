package com.campushare.post.controller;

import com.campushare.common.result.Result;
import com.campushare.post.service.DataInitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class DataInitController {

    private final DataInitService dataInitService;

    @PostMapping("/init-test-data")
    public Result<String> initTestData(
            @RequestParam(defaultValue = "10") int postsPerSchool) {
        String result = dataInitService.initTestData(postsPerSchool);
        return Result.success(result);
    }

    @PostMapping("/init-full-data")
    public Result<String> initFullTestData(
            @RequestParam(defaultValue = "10000") int userCount,
            @RequestParam(defaultValue = "2") int postsPerUser) {
        String result = dataInitService.initFullTestData(userCount, postsPerUser);
        return Result.success(result);
    }

    @PostMapping("/clear-posts")
    public Result<String> clearAllPosts() {
        String result = dataInitService.clearAllPosts();
        return Result.success(result);
    }

    @PostMapping("/init-targeted")
    public Result<String> initTargetedSchoolData(
            @RequestParam(defaultValue = "1000") int userCount,
            @RequestParam(defaultValue = "10") int postsPerUser,
            @RequestParam(defaultValue = "1") String schoolId) {
        String result = dataInitService.initTargetedSchoolData(userCount, postsPerUser, schoolId);
        return Result.success(result);
    }
}
