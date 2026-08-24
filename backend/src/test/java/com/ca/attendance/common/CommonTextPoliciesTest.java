package com.ca.attendance.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommonTextPoliciesTest {

    @Test
    void escapesSqlLikeMetacharactersForLiteralContainsSearch() {
        assertThat(SqlLike.contains(" 50%_done\\next "))
                .isEqualTo("%50\\%\\_done\\\\next%");
    }

    @Test
    void sanitizesAndBoundsDownloadFilenames() {
        String filename = DownloadFilename.xlsx("\u0000维修/培训:" + "长".repeat(120), "导出");

        assertThat(filename).endsWith(".xlsx");
        assertThat(filename).doesNotContain("\u0000", "/", ":");
        assertThat(filename.substring(0, filename.length() - 5)).hasSizeLessThanOrEqualTo(80);
    }
}
