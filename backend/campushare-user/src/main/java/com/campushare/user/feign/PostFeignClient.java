package com.campushare.user.feign;

import com.campushare.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "post-service", url = "${service.post.url:http://localhost:8082}")
public interface PostFeignClient {

    @GetMapping("/internal/posts/user/{userId}/posts")
    Result<PageData<PostListDTO>> getUserPosts(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/internal/posts/user/{userId}/starred")
    Result<PageData<PostListDTO>> getUserStarred(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/internal/posts/user/{userId}/liked")
    Result<PageData<PostListDTO>> getUserLiked(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/internal/posts/user/{userId}/history")
    Result<PageData<PostListDTO>> getUserHistory(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size);

    @GetMapping("/internal/posts/user/{userId}/stats")
    Result<UserPostStats> getUserStats(@PathVariable("userId") String userId);

    @PostMapping("/admin/init-creator-data")
    Result<String> initCreatorTestData(
            @RequestParam("userId") String userId,
            @RequestParam(value = "schoolId", defaultValue = "3") String schoolId);
}
