package com.parking.sql;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 保全性属性测试 — 验证未受 bug 影响的表定义在修复前后保持完全一致
 *
 * 测试策略：
 * - 读取原始 schema.sql 和修复后的 schema.sql（当前文件即为"修复后"版本）
 * - 对比每张未受 bug 影响的表的 CREATE TABLE 语句
 * - 在未修复代码上，两个文件相同，测试应 PASS（确认基线行为已捕获）
 * - 在修复后，测试仍应 PASS（确认无回归）
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4
 */
class SchemaPreservationPropertyTest {

    // 原始 schema.sql 文件路径（修复前的基线）
    private static final Path ORIGINAL_SCHEMA_PATH = Paths.get("src/main/resources/sql/schema.sql");

    // 修复后的 schema.sql 文件路径（当前文件即为修复后版本，未修复时与原始相同）
    private static final Path FIXED_SCHEMA_PATH = Paths.get("src/main/resources/sql/schema.sql");

    /**
     * 读取指定路径的文件内容
     */
    private String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("无法读取文件: " + path, e);
        }
    }

    /**
     * 从 SQL 文件内容中提取指定表的 CREATE TABLE 语句
     *
     * 匹配模式：从 CREATE TABLE ... tableName 开始，到该语句的 ENGINE=... 行结束（含注释）
     * 支持 IF NOT EXISTS 语法
     */
    private String extractCreateTableDDL(String sqlContent, String tableName) {
        // 匹配 CREATE TABLE [IF NOT EXISTS] tableName ( ... ) ENGINE=... COMMENT='...';
        // 使用非贪婪匹配，确保只匹配到第一个完整的 ENGINE 子句
        String regex = "(CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?"
                + Pattern.quote(tableName)
                + "\\s*\\(.*?\\)\\s*ENGINE=InnoDB[^;]*;)";
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(sqlContent);

        if (matcher.find()) {
            return normalizeWhitespace(matcher.group(1));
        }
        return null;
    }

    /**
     * 规范化空白字符，便于对比
     * 将连续空白替换为单个空格，去除首尾空白
     */
    private String normalizeWhitespace(String text) {
        if (text == null) return null;
        return text.replaceAll("\\s+", " ").trim();
    }

    // ========================================================================
    // 属性测试：验证未受 bug 影响的表定义在修复前后完全一致
    // ========================================================================

    /**
     * 属性 1：基础业务表的 CREATE TABLE 语句在修复前后保持一致
     *
     * 验证 sys_community、sys_admin、sys_owner、sys_house、
     * sys_owner_house_rel、sys_car_plate（CREATE TABLE 部分）、parking_config
     * 的定义不受修复影响。
     *
     * Validates: Requirements 3.1
     */
    @Property
    void basicBusinessTableDDLShouldBePreserved(@ForAll("basicBusinessTables") String tableName) {
        String originalContent = readFile(ORIGINAL_SCHEMA_PATH);
        String fixedContent = readFile(FIXED_SCHEMA_PATH);

        String originalDDL = extractCreateTableDDL(originalContent, tableName);
        String fixedDDL = extractCreateTableDDL(fixedContent, tableName);

        assertThat(originalDDL)
                .as("原始 schema.sql 中应能找到表 %s 的 CREATE TABLE 语句", tableName)
                .isNotNull();

        assertThat(fixedDDL)
                .as("修复后 schema.sql 中应能找到表 %s 的 CREATE TABLE 语句", tableName)
                .isNotNull();

        assertThat(fixedDDL)
                .as("表 %s 的 CREATE TABLE 语句在修复前后应完全一致", tableName)
                .isEqualTo(originalDDL);
    }

    @Provide
    Arbitrary<String> basicBusinessTables() {
        return Arbitraries.of(
                "sys_community",
                "sys_admin",
                "sys_owner",
                "sys_house",
                "sys_owner_house_rel",
                "sys_car_plate",
                "parking_config"
        );
    }

    /**
     * 属性 2：分表模板和静态分表的定义在修复前后保持一致
     *
     * 验证 parking_car_record_template 及
     * parking_car_record_202603 ~ parking_car_record_202606 的定义不受修复影响。
     * 注意：静态分表使用 CREATE TABLE ... LIKE 语法，需要单独匹配。
     *
     * Validates: Requirements 3.2
     */
    @Property
    void shardingTableDDLShouldBePreserved(@ForAll("shardingTables") String tableName) {
        String originalContent = readFile(ORIGINAL_SCHEMA_PATH);
        String fixedContent = readFile(FIXED_SCHEMA_PATH);

        if ("parking_car_record_template".equals(tableName)) {
            // 模板表使用标准 CREATE TABLE 语法
            String originalDDL = extractCreateTableDDL(originalContent, tableName);
            String fixedDDL = extractCreateTableDDL(fixedContent, tableName);

            assertThat(originalDDL)
                    .as("原始 schema.sql 中应能找到表 %s 的 CREATE TABLE 语句", tableName)
                    .isNotNull();
            assertThat(fixedDDL)
                    .as("修复后 schema.sql 中应能找到表 %s 的 CREATE TABLE 语句", tableName)
                    .isNotNull();
            assertThat(fixedDDL)
                    .as("表 %s 的 CREATE TABLE 语句在修复前后应完全一致", tableName)
                    .isEqualTo(originalDDL);
        } else {
            // 静态分表使用 CREATE TABLE ... LIKE 语法
            String originalLike = extractCreateTableLikeDDL(originalContent, tableName);
            String fixedLike = extractCreateTableLikeDDL(fixedContent, tableName);

            assertThat(originalLike)
                    .as("原始 schema.sql 中应能找到表 %s 的 CREATE TABLE ... LIKE 语句", tableName)
                    .isNotNull();
            assertThat(fixedLike)
                    .as("修复后 schema.sql 中应能找到表 %s 的 CREATE TABLE ... LIKE 语句", tableName)
                    .isNotNull();
            assertThat(fixedLike)
                    .as("表 %s 的 CREATE TABLE ... LIKE 语句在修复前后应完全一致", tableName)
                    .isEqualTo(originalLike);
        }
    }

    /**
     * 从 SQL 文件内容中提取 CREATE TABLE ... LIKE 语句及其后续的 ALTER TABLE COMMENT 语句
     */
    private String extractCreateTableLikeDDL(String sqlContent, String tableName) {
        // 匹配 CREATE TABLE IF NOT EXISTS tableName LIKE template;
        // 以及紧随其后的 ALTER TABLE tableName COMMENT='...';
        String regex = "(CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?"
                + Pattern.quote(tableName)
                + "\\s+LIKE\\s+\\w+;\\s*"
                + "ALTER\\s+TABLE\\s+" + Pattern.quote(tableName) + "\\s+COMMENT=[^;]*;)";
        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(sqlContent);

        if (matcher.find()) {
            return normalizeWhitespace(matcher.group(1));
        }
        return null;
    }

    @Provide
    Arbitrary<String> shardingTables() {
        return Arbitraries.of(
                "parking_car_record_template",
                "parking_car_record_202603",
                "parking_car_record_202604",
                "parking_car_record_202605",
                "parking_car_record_202606"
        );
    }

    /**
     * 属性 3：Visitor 相关表的定义在修复前后保持一致
     *
     * 验证 visitor_application、visitor_authorization、visitor_session
     * 的定义不受修复影响。
     *
     * Validates: Requirements 3.3
     */
    @Property
    void visitorTableDDLShouldBePreserved(@ForAll("visitorTables") String tableName) {
        String originalContent = readFile(ORIGINAL_SCHEMA_PATH);
        String fixedContent = readFile(FIXED_SCHEMA_PATH);

        String originalDDL = extractCreateTableDDL(originalContent, tableName);
        String fixedDDL = extractCreateTableDDL(fixedContent, tableName);

        assertThat(originalDDL)
                .as("原始 schema.sql 中应能找到表 %s 的 CREATE TABLE 语句", tableName)
                .isNotNull();
        assertThat(fixedDDL)
                .as("修复后 schema.sql 中应能找到表 %s 的 CREATE TABLE 语句", tableName)
                .isNotNull();
        assertThat(fixedDDL)
                .as("表 %s 的 CREATE TABLE 语句在修复前后应完全一致", tableName)
                .isEqualTo(originalDDL);
    }

    @Provide
    Arbitrary<String> visitorTables() {
        return Arbitraries.of(
                "visitor_application",
                "visitor_authorization",
                "visitor_session"
        );
    }

    /**
     * 属性 4：辅助功能表的定义在修复前后保持一致
     *
     * 验证 parking_stat_daily、sys_ip_whitelist、zombie_vehicle、
     * owner_info_modify_application、export_task、verification_code、
     * hardware_device、parking_fee 的定义不受修复影响。
     *
     * Validates: Requirements 3.4
     */
    @Property
    void auxiliaryTableDDLShouldBePreserved(@ForAll("auxiliaryTables") String tableName) {
        String originalContent = readFile(ORIGINAL_SCHEMA_PATH);
        String fixedContent = readFile(FIXED_SCHEMA_PATH);

        String originalDDL = extractCreateTableDDL(originalContent, tableName);
        String fixedDDL = extractCreateTableDDL(fixedContent, tableName);

        assertThat(originalDDL)
                .as("原始 schema.sql 中应能找到表 %s 的 CREATE TABLE 语句", tableName)
                .isNotNull();
        assertThat(fixedDDL)
                .as("修复后 schema.sql 中应能找到表 %s 的 CREATE TABLE 语句", tableName)
                .isNotNull();
        assertThat(fixedDDL)
                .as("表 %s 的 CREATE TABLE 语句在修复前后应完全一致", tableName)
                .isEqualTo(originalDDL);
    }

    @Provide
    Arbitrary<String> auxiliaryTables() {
        return Arbitraries.of(
                "parking_stat_daily",
                "sys_ip_whitelist",
                "zombie_vehicle",
                "owner_info_modify_application",
                "export_task",
                "verification_code",
                "hardware_device",
                "parking_fee"
        );
    }
}
