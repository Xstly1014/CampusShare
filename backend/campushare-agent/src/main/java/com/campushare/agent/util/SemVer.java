package com.campushare.agent.util;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义化版本号（SemVer）工具类。
 *
 * 格式：vMAJOR.MINOR.PATCH（如 v1.0.0）
 * - MAJOR：不兼容的 API 变更
 * - MINOR：向下兼容的功能新增
 * - PATCH：向下兼容的缺陷修复（知识库文档更新默认走 patch 递增）
 *
 * 不可变对象，线程安全。
 */
public final class SemVer implements Comparable<SemVer> {

    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^v?(\\d+)\\.(\\d+)\\.(\\d+)$"
    );

    private final int major;
    private final int minor;
    private final int patch;

    private SemVer(int major, int minor, int patch) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("SemVer components must be non-negative: " + major + "." + minor + "." + patch);
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /**
     * 解析 SemVer 字符串，接受 "v1.0.0" 或 "1.0.0"。
     */
    public static SemVer parse(String version) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Version string must not be null or blank");
        }
        Matcher matcher = SEMVER_PATTERN.matcher(version.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid SemVer format: " + version + " (expected vMAJOR.MINOR.PATCH)");
        }
        return new SemVer(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        );
    }

    /**
     * 初始版本 v1.0.0。
     */
    public static SemVer initial() {
        return new SemVer(1, 0, 0);
    }

    /**
     * 安全解析，解析失败返回 v1.0.0。
     */
    public static SemVer parseOrInitial(String version) {
        if (version == null || version.isBlank()) {
            return initial();
        }
        try {
            return parse(version);
        } catch (IllegalArgumentException e) {
            return initial();
        }
    }

    public int major() { return major; }
    public int minor() { return minor; }
    public int patch() { return patch; }

    public SemVer nextPatch() {
        return new SemVer(major, minor, patch + 1);
    }

    public SemVer nextMinor() {
        return new SemVer(major, minor + 1, 0);
    }

    public SemVer nextMajor() {
        return new SemVer(major + 1, 0, 0);
    }

    @Override
    public int compareTo(SemVer o) {
        int c = Integer.compare(this.major, o.major);
        if (c != 0) return c;
        c = Integer.compare(this.minor, o.minor);
        if (c != 0) return c;
        return Integer.compare(this.patch, o.patch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemVer semVer)) return false;
        return major == semVer.major && minor == semVer.minor && patch == semVer.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return "v" + major + "." + minor + "." + patch;
    }
}
