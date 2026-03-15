package com.parking.sql;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bug Condition 探索性属性测试
 *
 * 通过静态分析原始 schema.sql 文件，验证 Docker entrypoint 兼容性问题。
 * 此测试在未修复代码上预期 FAIL — 失败即确认 bug 存在。
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4, 1.5
 */
class SchemaInitBugConditionPropertyTest {

    // schema.sql 文件路径
    private static final Path SCHEMA_PATH = Paths.get("src/main/resources/sql/schema.sql");

    /**
     * 读取 schema.sql 文件内容
     */
    private String readSchemaFile() {
        try {
            return Files.readString(SCHEMA_PATH);
        } catch (IOException e) {
            throw new RuntimeException("无法读取 schema.sql 文件: " + SCHEMA_PATH, e);
        }
    }

    /**
     * 属性 1：验证 SQL 文件不包含 DELIMITER 关键字
     *
     * Docker entrypoint 通过 shell 管道（mysql < file.sql）执行 SQL 文件，
     * DELIMITER 是 MySQL 客户端命令而非 SQL 语句，在管道模式下无法正确解析。
     *
     * Validates: Requirements 1.1
     */
    @Property
    void schemaFileShouldNotContainDelimiter(@ForAll("delimiterPatterns") String delimiterPattern) {
        String content = readSchemaFile();
        // 使用不区分大小写的匹配，检查文件中是否包含 DELIMITER 关键字
        boolean containsDelimiter = Pattern.compile(delimiterPattern, Pattern.CASE_INSENSITIVE)
                .matcher(content)
                .find();
        assertThat(containsDelimiter)
                .as("schema.sql 不应包含 DELIMITER 语法（匹配模式: %s），因为 Docker entrypoint 的管道模式无法解析", delimiterPattern)
                .isFalse();
    }

    @Provide
    Arbitrary<String> delimiterPatterns() {
        return Arbitraries.of(
                "\\bDELIMITER\\b",           // 精确匹配 DELIMITER 关键字
                "DELIMITER\\s+\\$",           // 匹配 DELIMITER $
                "DELIMITER\\s+;"              // 匹配 DELIMITER ;
        );
    }

    /**
     * 属性 2：验证 SQL 文件不包含 CREATE DATABASE 语句
     *
     * Docker 已通过 MYSQL_DATABASE 环境变量创建数据库，
     * schema.sql 中的 CREATE DATABASE 是冗余操作。
     *
     * Validates: Requirements 1.2
     */
    @Property
    void schemaFileShouldNotContainCreateDatabase(@ForAll("createDatabasePatterns") String pattern) {
        String content = readSchemaFile();
        boolean containsCreateDB = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                .matcher(content)
                .find();
        assertThat(containsCreateDB)
                .as("schema.sql 不应包含 CREATE DATABASE 语句（匹配模式: %s），因为 Docker MYSQL_DATABASE 环境变量已负责创建数据库", pattern)
                .isFalse();
    }

    @Provide
    Arbitrary<String> createDatabasePatterns() {
        return Arbitraries.of(
                "CREATE\\s+DATABASE",                          // 基本 CREATE DATABASE
                "CREATE\\s+DATABASE\\s+IF\\s+NOT\\s+EXISTS"    // CREATE DATABASE IF NOT EXISTS
        );
    }

    /**
     * 属性 3：验证 SQL 文件不包含 USE parking_db 语句
     *
     * Docker entrypoint 脚本会自动在 MYSQL_DATABASE 指定的数据库上下文中执行 SQL 文件，
     * USE parking_db 在管道执行模式下可能引发上下文切换问题。
     *
     * Validates: Requirements 1.2
     */
    @Property
    void schemaFileShouldNotContainUseStatement(@ForAll("usePatterns") String pattern) {
        String content = readSchemaFile();
        boolean containsUse = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                .matcher(content)
                .find();
        assertThat(containsUse)
                .as("schema.sql 不应包含 USE parking_db 语句（匹配模式: %s），因为 Docker entrypoint 已自动切换到目标数据库", pattern)
                .isFalse();
    }

    @Provide
    Arbitrary<String> usePatterns() {
        return Arbitraries.of(
                "^\\s*USE\\s+parking_db\\s*;",   // 行首 USE parking_db;
                "\\bUSE\\s+parking_db\\b"         // 任意位置 USE parking_db
        );
    }

    /**
     * 属性 4：验证所有分区表的主键包含分区列
     *
     * MySQL 要求分区表的分区表达式中引用的所有列必须包含在表的每个唯一索引（含主键）中。
     * sys_operation_log 使用 PARTITION BY RANGE (TO_DAYS(operation_time))，主键必须包含 operation_time。
     * sys_access_log 使用 PARTITION BY RANGE (TO_DAYS(access_time))，主键必须包含 access_time。
     *
     * Validates: Requirements 1.3, 1.4
     */
    @Property
    void partitionedTablePrimaryKeyShouldIncludePartitionColumn(
            @ForAll("partitionedTables") PartitionedTableInfo tableInfo) {
        String content = readSchemaFile();

        // 提取该表的 CREATE TABLE 语句（包含 PARTITION BY）
        String tablePattern = "CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" + tableInfo.tableName
                + "\\s*\\((.+?)\\)\\s*ENGINE";
        Matcher tableMatcher = Pattern.compile(tablePattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(content);

        assertThat(tableMatcher.find())
                .as("应能找到表 %s 的 CREATE TABLE 语句", tableInfo.tableName)
                .isTrue();

        String tableBody = tableMatcher.group(1);

        // 检查主键是否包含分区列
        // 情况 1：复合主键 PRIMARY KEY (id, partition_column)
        boolean hasCompositePK = Pattern.compile(
                        "PRIMARY\\s+KEY\\s*\\([^)]*\\b" + tableInfo.partitionColumn + "\\b[^)]*\\)",
                        Pattern.CASE_INSENSITIVE)
                .matcher(tableBody)
                .find();

        // 情况 2：内联主键 partition_column ... PRIMARY KEY（不太可能但也检查）
        boolean hasInlinePK = Pattern.compile(
                        "\\b" + tableInfo.partitionColumn + "\\b[^,]*PRIMARY\\s+KEY",
                        Pattern.CASE_INSENSITIVE)
                .matcher(tableBody)
                .find();

        assertThat(hasCompositePK || hasInlinePK)
                .as("分区表 %s 的主键应包含分区列 %s，否则 MySQL 会报错 'A PRIMARY KEY must include all columns in the table's partitioning function'",
                        tableInfo.tableName, tableInfo.partitionColumn)
                .isTrue();
    }

    @Provide
    Arbitrary<PartitionedTableInfo> partitionedTables() {
        return Arbitraries.of(
                new PartitionedTableInfo("sys_operation_log", "operation_time"),
                new PartitionedTableInfo("sys_access_log", "access_time")
        );
    }

    /**
     * 分区表信息记录
     */
    record PartitionedTableInfo(String tableName, String partitionColumn) {
        @Override
        public String toString() {
            return tableName + " (分区列: " + partitionColumn + ")";
        }
    }
}
