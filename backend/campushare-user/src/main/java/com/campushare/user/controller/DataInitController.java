package com.campushare.user.controller;

import com.campushare.common.result.Result;
import com.campushare.user.service.DataInitService;
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

    @PostMapping("/clear-posts")
    public Result<String> clearAllPosts() {
        String result = dataInitService.clearAllPosts();
        return Result.success(result);
    }
}
