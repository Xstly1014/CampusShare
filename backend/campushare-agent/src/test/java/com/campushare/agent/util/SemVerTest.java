package com.campushare.agent.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SemVer 工具类单元测试。
 *
 * 验证点：
 *  - parse 接受 v1.0.0 / 1.0.0 两种格式
 *  - parse null / blank / 非法格式抛 IllegalArgumentException
 *  - initial() = v1.0.0
 *  - parseOrInitial 安全降级
 *  - nextPatch / nextMinor / nextMajor 递增
 *  - compareTo 排序正确
 *  - equals / hashCode
 *  - toString 输出 "v1.0.0"
 */
@DisplayName("SemVer 工具类单元测试")
class SemVerTest {

    // ========== parse ==========

    @Nested
    @DisplayName("parse：解析版本号字符串")
    class Parse {

        @Test
        @DisplayName("v1.0.0 → 正确解析")
        void parse_vPrefix_returnsSemVer() {
            SemVer v = SemVer.parse("v1.0.0");
            assertThat(v.major()).isEqualTo(1);
            assertThat(v.minor()).isEqualTo(0);
            assertThat(v.patch()).isEqualTo(0);
        }

        @Test
        @DisplayName("1.2.3 → 正确解析（无 v 前缀）")
        void parse_noVPrefix_returnsSemVer() {
            SemVer v = SemVer.parse("1.2.3");
            assertThat(v.major()).isEqualTo(1);
            assertThat(v.minor()).isEqualTo(2);
            assertThat(v.patch()).isEqualTo(3);
        }

        @Test
        @DisplayName("v10.20.30 → 大版本号正确解析")
        void parse_largeNumbers_returnsSemVer() {
            SemVer v = SemVer.parse("v10.20.30");
            assertThat(v.toString()).isEqualTo("v10.20.30");
        }

        @Test
        @DisplayName("null → 抛 IllegalArgumentException")
        void parse_null_throwsException() {
            assertThatThrownBy(() -> SemVer.parse(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be null or blank");
        }

        @Test
        @DisplayName("空字符串 → 抛 IllegalArgumentException")
        void parse_empty_throwsException() {
            assertThatThrownBy(() -> SemVer.parse(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("空白字符串 → 抛 IllegalArgumentException")
        void parse_blank_throwsException() {
            assertThatThrownBy(() -> SemVer.parse("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("非法格式 'v1.0' → 抛 IllegalArgumentException")
        void parse_invalidFormat_throwsException() {
            assertThatThrownBy(() -> SemVer.parse("v1.0"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid SemVer format");
        }

        @Test
        @DisplayName("非法格式 'v1.0.0-rc1' → 抛 IllegalArgumentException（不支持预发布标识）")
        void parse_preReleaseSuffix_throwsException() {
            assertThatThrownBy(() -> SemVer.parse("v1.0.0-rc1"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("带空格的 'v1.0.0 ' → trim 后正确解析")
        void parse_withSpaces_trimsAndParses() {
            SemVer v = SemVer.parse("  v1.0.0  ");
            assertThat(v.toString()).isEqualTo("v1.0.0");
        }
    }

    // ========== initial / parseOrInitial ==========

    @Nested
    @DisplayName("initial / parseOrInitial：初始版本与安全解析")
    class InitialAndSafeParse {

        @Test
        @DisplayName("initial() = v1.0.0")
        void initial_returnsV1_0_0() {
            SemVer v = SemVer.initial();
            assertThat(v.toString()).isEqualTo("v1.0.0");
            assertThat(v.major()).isEqualTo(1);
            assertThat(v.minor()).isEqualTo(0);
            assertThat(v.patch()).isEqualTo(0);
        }

        @Test
        @DisplayName("parseOrInitial(null) → v1.0.0")
        void parseOrInitial_null_returnsInitial() {
            SemVer v = SemVer.parseOrInitial(null);
            assertThat(v.toString()).isEqualTo("v1.0.0");
        }

        @Test
        @DisplayName("parseOrInitial('') → v1.0.0")
        void parseOrInitial_empty_returnsInitial() {
            SemVer v = SemVer.parseOrInitial("");
            assertThat(v.toString()).isEqualTo("v1.0.0");
        }

        @Test
        @DisplayName("parseOrInitial('invalid') → v1.0.0（降级）")
        void parseOrInitial_invalid_returnsInitial() {
            SemVer v = SemVer.parseOrInitial("invalid");
            assertThat(v.toString()).isEqualTo("v1.0.0");
        }

        @Test
        @DisplayName("parseOrInitial('v2.3.4') → v2.3.4（正常解析）")
        void parseOrInitial_valid_returnsParsed() {
            SemVer v = SemVer.parseOrInitial("v2.3.4");
            assertThat(v.toString()).isEqualTo("v2.3.4");
        }
    }

    // ========== nextPatch / nextMinor / nextMajor ==========

    @Nested
    @DisplayName("nextPatch / nextMinor / nextMajor：版本递增")
    class NextVersion {

        @Test
        @DisplayName("v1.0.0.nextPatch() = v1.0.1")
        void nextPatch_incrementsPatch() {
            assertThat(SemVer.parse("v1.0.0").nextPatch().toString()).isEqualTo("v1.0.1");
        }

        @Test
        @DisplayName("v1.2.3.nextPatch() = v1.2.4")
        void nextPatch_incrementsPatchFromNonZero() {
            assertThat(SemVer.parse("v1.2.3").nextPatch().toString()).isEqualTo("v1.2.4");
        }

        @Test
        @DisplayName("v1.0.0.nextMinor() = v1.1.0")
        void nextMinor_incrementsMinor_resetsPatch() {
            assertThat(SemVer.parse("v1.0.0").nextMinor().toString()).isEqualTo("v1.1.0");
        }

        @Test
        @DisplayName("v1.2.5.nextMinor() = v1.3.0（patch 归零）")
        void nextMinor_resetsPatch() {
            assertThat(SemVer.parse("v1.2.5").nextMinor().toString()).isEqualTo("v1.3.0");
        }

        @Test
        @DisplayName("v1.0.0.nextMajor() = v2.0.0")
        void nextMajor_incrementsMajor_resetsMinorAndPatch() {
            assertThat(SemVer.parse("v1.0.0").nextMajor().toString()).isEqualTo("v2.0.0");
        }

        @Test
        @DisplayName("v3.7.2.nextMajor() = v4.0.0（minor/patch 归零）")
        void nextMajor_resetsMinorAndPatch() {
            assertThat(SemVer.parse("v3.7.2").nextMajor().toString()).isEqualTo("v4.0.0");
        }

        @Test
        @DisplayName("递增不修改原对象（不可变性）")
        void nextVersion_doesNotModifyOriginal() {
            SemVer original = SemVer.parse("v1.2.3");
            original.nextPatch();
            original.nextMinor();
            original.nextMajor();
            assertThat(original.toString()).isEqualTo("v1.2.3");
        }
    }

    // ========== compareTo ==========

    @Nested
    @DisplayName("compareTo：版本排序")
    class CompareTo {

        @Test
        @DisplayName("v1.0.0 < v1.0.1")
        void compareTo_patchDifference() {
            assertThat(SemVer.parse("v1.0.0").compareTo(SemVer.parse("v1.0.1"))).isNegative();
            assertThat(SemVer.parse("v1.0.1").compareTo(SemVer.parse("v1.0.0"))).isPositive();
        }

        @Test
        @DisplayName("v1.0.0 < v1.1.0")
        void compareTo_minorDifference() {
            assertThat(SemVer.parse("v1.0.0").compareTo(SemVer.parse("v1.1.0"))).isNegative();
        }

        @Test
        @DisplayName("v1.5.0 < v2.0.0")
        void compareTo_majorDifference() {
            assertThat(SemVer.parse("v1.5.0").compareTo(SemVer.parse("v2.0.0"))).isNegative();
        }

        @Test
        @DisplayName("v1.0.0 = v1.0.0 → 0")
        void compareTo_equal_returnsZero() {
            assertThat(SemVer.parse("v1.0.0").compareTo(SemVer.parse("v1.0.0"))).isZero();
        }

        @Test
        @DisplayName("完整排序：v1.0.0 < v1.0.1 < v1.1.0 < v1.2.0 < v2.0.0")
        void compareTo_fullSort() {
            List<SemVer> versions = new ArrayList<>(List.of(
                    SemVer.parse("v2.0.0"),
                    SemVer.parse("v1.0.1"),
                    SemVer.parse("v1.1.0"),
                    SemVer.parse("v1.0.0"),
                    SemVer.parse("v1.2.0")
            ));
            versions.sort(null);
            assertThat(versions).extracting(SemVer::toString)
                    .containsExactly("v1.0.0", "v1.0.1", "v1.1.0", "v1.2.0", "v2.0.0");
        }
    }

    // ========== equals / hashCode ==========

    @Nested
    @DisplayName("equals / hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("相同版本号 → equals=true")
        void equals_sameVersion_returnsTrue() {
            assertThat(SemVer.parse("v1.0.0")).isEqualTo(SemVer.parse("v1.0.0"));
            assertThat(SemVer.parse("v1.0.0")).isEqualTo(SemVer.parse("1.0.0"));
        }

        @Test
        @DisplayName("不同版本号 → equals=false")
        void equals_differentVersion_returnsFalse() {
            assertThat(SemVer.parse("v1.0.0")).isNotEqualTo(SemVer.parse("v1.0.1"));
            assertThat(SemVer.parse("v1.0.0")).isNotEqualTo(SemVer.parse("v2.0.0"));
        }

        @Test
        @DisplayName("与 null 比较 → equals=false")
        void equals_null_returnsFalse() {
            assertThat(SemVer.parse("v1.0.0").equals(null)).isFalse();
        }

        @Test
        @DisplayName("与非 SemVer 对象比较 → equals=false")
        void equals_nonSemVer_returnsFalse() {
            assertThat(SemVer.parse("v1.0.0").equals("v1.0.0")).isFalse();
        }

        @Test
        @DisplayName("与自身比较 → equals=true")
        void equals_self_returnsTrue() {
            SemVer v = SemVer.parse("v1.0.0");
            assertThat(v.equals(v)).isTrue();
        }

        @Test
        @DisplayName("hashCode 一致性：相同版本号 hashCode 相同")
        void hashCode_sameVersion_sameHash() {
            assertThat(SemVer.parse("v1.0.0").hashCode())
                    .isEqualTo(SemVer.parse("1.0.0").hashCode());
        }
    }

    // ========== toString ==========

    @Nested
    @DisplayName("toString")
    class ToString {

        @Test
        @DisplayName("v1.0.0 → 'v1.0.0'")
        void toString_returnsFormatted() {
            assertThat(SemVer.parse("v1.0.0").toString()).isEqualTo("v1.0.0");
        }

        @Test
        @DisplayName("1.2.3 → 'v1.2.3'（补 v 前缀）")
        void toString_addsVPrefix() {
            assertThat(SemVer.parse("1.2.3").toString()).isEqualTo("v1.2.3");
        }

        @Test
        @DisplayName("initial().toString() = 'v1.0.0'")
        void toString_initial() {
            assertThat(SemVer.initial().toString()).isEqualTo("v1.0.0");
        }
    }
}
