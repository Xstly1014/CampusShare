package com.campushare.user.feign;

import lombok.Data;

import java.util.List;

@Data
public class PageData<T> {
    private List<T> records;
    private long total;
    private long size;
    private long current;
    private long pages;
}
