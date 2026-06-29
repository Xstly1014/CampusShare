package com.campushare.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户信息响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    
    /**
     * 用户ID
     */
    private String id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
    
    /**
     * 个人简介
     */
    private String bio;
    
    /**
     * 所属学校ID
     */
    private String schoolId;
    
    /**
     * 所属学校名称
     */
    private String schoolName;
    
    /**
     * 创建时间
     */
    private String createTime;

    @JsonProperty("isCreator")
    private boolean creator;

    @JsonProperty("isAdmin")
    private boolean admin;
}