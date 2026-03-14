package com.parking.service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 缓存管理服务接口
 * 提供通用的缓存操作方法，支持缓存键生成、缓存失效和过期时间管理
 * 缓存键格式: {resource}:{communityId}:{houseNo}
 * Validates: Requirements 21.7, 21.8
 */
public interface CacheService {

    /**
     * 获取缓存值
     *
     * @param key 缓存键
     * @return 缓存值，如果不存在则返回 empty
     */
    Optional<Object> get(String key);

    /**
     * 获取缓存值并转换为指定类型
     *
     * @param key  缓存键
     * @param type 目标类型
     * @param <T>  返回值类型
     * @return 缓存值，如果不存在则返回 empty
     */
    <T> Optional<T> get(String key, Class<T> type);

    /**
     * 设置缓存值（带过期时间）
     *
     * @param key     缓存键
     * @param value   缓存值
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    void set(String key, Object value, long timeout, TimeUnit unit);

    /**
     * 删除缓存
     *
     * @param key 缓存键
     * @return true 表示删除成功，false 表示键不存在
     */
    boolean delete(String key);

    /**
     * 检查缓存键是否存在
     *
     * @param key 缓存键
     * @return true 表示存在，false 表示不存在
     */
    boolean exists(String key);

    /**
     * 按前缀批量删除缓存
     * 用于失效某个小区或资源类型下的所有缓存
     *
     * @param prefix 缓存键前缀（如 report:1001）
     * @return 删除的键数量
     */
    long deleteByPrefix(String prefix);

    /**
     * 生成缓存键
     * 格式: {resource}:{communityId}:{houseNo}
     *
     * @param resource    资源类型（如 report, ip_whitelist, vehicles）
     * @param communityId 小区 ID
     * @param houseNo     房屋号
     * @return 生成的缓存键
     */
    String generateKey(String resource, Long communityId, String houseNo);

    /**
     * 生成缓存键（不含 houseNo）
     * 格式: {resource}:{communityId}
     *
     * @param resource    资源类型
     * @param communityId 小区 ID
     * @return 生成的缓存键
     */
    String generateKey(String resource, Long communityId);
}
