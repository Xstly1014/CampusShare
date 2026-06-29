package com.campushare.post.service;

public interface DataInitService {
    String clearAllPosts();
    String initTestData(int postsPerSchool);
    String initFullTestData(int userCount, int postsPerUser);
    String initTargetedSchoolData(int userCount, int postsPerUser, String schoolId);
    String initCreatorTestData(String userId, String schoolId);
}
