package com.iflytek.astra.console.toolkit.util;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Redis utility class (based on Spring {@link RedisTemplate}).
 *
 * <p>
 * Features:
 * </p>
 * <ol>
 * <li>Distributed lock with token: {@code tryLock / renew / unlock} (Lua scripts ensure "owner-only
 * release").</li>
 * <li>Safe SCAN / batch deletion / multi-key operations.</li>
 * <li>Common KV / Set / Hash wrappers.</li>
 * </ol>
 *
 * <p>
 * Notes:
 * </p>
 * <ul>
 * <li>This utility defaults to {@code RedisTemplate<String, Object>}. Ensure the project's
 * serialization policy is consistent.</li>
 * <li>The distributed lock here is for simple mutual exclusion only. For complex transactions /
 * strong consistency, adopt a more complete coordination mechanism.</li>
 * </ul>
 */
@Slf4j
@Component
public class RedisUtil {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /* ========================= Constants & Precompiled Scripts ========================= */

    private static final String PLACEHOLDER = "1";
    private static final int DEFAULT_SCAN_COUNT = 1000;
    private static final int BATCH_DELETE_SIZE = 1000;

    // Renew only when "lock value == token"
    private static final DefaultRedisScript<Long> LUA_RENEW =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "  return redis.call('pexpire', KEYS[1], ARGV[2]) " +
                            "else return 0 end",
                    Long.class);

    // Delete only when "lock value == token"
    private static final DefaultRedisScript<Long> LUA_UNLOCK =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                            "  return redis.call('del', KEYS[1]) " +
                            "else return 0 end",
                    Long.class);

    /* ========================= Distributed Lock (with token) ========================= */

    /**
     * Acquire a distributed lock (with token). Underlying command:
     * {@code SET key token NX EX ttlSeconds}.
     *
     * @param key lock key (required)
     * @param ttlSeconds expiration time in seconds (must be {@code >= 1}, smaller values are coerced to
     *        1)
     * @param token lock owner token (recommended to be generated and stored by caller; if {@code null},
     *        a UUID will be generated)
     * @return {@code true} if locked; {@code false} if already held by others
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public boolean tryLock(String key, long ttlSeconds, @Nullable String token) {
        requireKey(key);
        long ttl = Math.max(1, ttlSeconds);
        String val = token != null ? token : UUID.randomUUID().toString();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, val, ttl, TimeUnit.SECONDS);
        log.debug("redis.tryLock key={}, ttl={}s, token={}, ok={}", key, ttl, safe(val), ok);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Acquire a distributed lock (with token).
     *
     * @param key lock key (required)
     * @param ttl expiration duration (required; values {@code < 1s} will be coerced to 1s)
     * @param token lock owner token (nullable; a UUID will be generated when {@code null})
     * @return {@code true} if locked; {@code false} otherwise
     * @throws NullPointerException if {@code ttl} is null
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public boolean tryLock(String key, Duration ttl, @Nullable String token) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        return tryLock(key, Math.max(1, ttl.getSeconds()), token);
    }

    /**
     * Renew the lock: refresh expiration only when it is still held by the given token.
     *
     * @param key lock key (required)
     * @param ttlSeconds expiration time in seconds (must be {@code >= 1})
     * @param token lock owner token (required)
     * @return {@code true} if renewed; {@code false} when the lock does not exist or is held by others
     * @throws IllegalArgumentException if {@code key} is null/empty or {@code token} is null
     */
    public boolean renew(String key, long ttlSeconds, String token) {
        requireKey(key);
        Objects.requireNonNull(token, "token must not be null");
        Long ret = redisTemplate.execute(LUA_RENEW,
                Collections.singletonList(key),
                token,
                String.valueOf(Math.max(1, ttlSeconds) * 1000));
        boolean ok = ret != null && ret > 0;
        log.debug("redis.renew key={}, ttl={}s, token={}, ok={}", key, ttlSeconds, safe(token), ok);
        return ok;
    }

    /**
     * Renew the lock: refresh expiration only when it is still held by the given token.
     *
     * @param key lock key (required)
     * @param ttl expiration duration (required; values {@code < 1s} will be coerced to 1s)
     * @param token lock owner token (required)
     * @return {@code true} if renewed; {@code false} otherwise
     * @throws NullPointerException if {@code ttl} is null
     * @throws IllegalArgumentException if {@code key} is null/empty or {@code token} is null
     */
    public boolean renew(String key, Duration ttl, String token) {
        Objects.requireNonNull(ttl, "ttl must not be null");
        return renew(key, Math.max(1, ttl.getSeconds()), token);
    }

    /**
     * Release the lock: delete only when it is still held by the given token.
     *
     * @param key lock key (required)
     * @param token lock owner token (required)
     * @return {@code true} if released; {@code false} when the lock does not exist or is held by others
     * @throws IllegalArgumentException if {@code key} is null/empty or {@code token} is null
     */
    public boolean unlock(String key, String token) {
        requireKey(key);
        Objects.requireNonNull(token, "token must not be null");
        Long ret = redisTemplate.execute(LUA_UNLOCK, Collections.singletonList(key), token);
        boolean ok = ret != null && ret > 0;
        log.debug("redis.unlock key={}, token={}, ok={}", key, safe(token), ok);
        return ok;
    }

    /* ========================= Legacy API (without token) ========================= */

    /**
     * Legacy lock without token: uses a fixed placeholder as value.
     * <p>
     * <b>Warning:</b> Only suitable for single-instance / low-risk tasks; not recommended in
     * distributed environments.
     * </p>
     *
     * @param key lock key (required)
     * @param seconds expiration seconds
     * @return {@code true} if locked; {@code false} otherwise
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    @Deprecated
    public boolean tryLock(String key, long seconds) {
        requireKey(key);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, PLACEHOLDER, seconds, TimeUnit.SECONDS);
        log.warn("redis.tryLock(deprecated) key={}, ttl={}s, ok={}", key, seconds, ok);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Legacy unlock without ownership check; may delete others' lock mistakenly.
     *
     * @param key lock key (required)
     * @return {@code true} if deleted; {@code false} otherwise
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    @Deprecated
    public boolean unlock(String key) {
        requireKey(key);
        Boolean ok = redisTemplate.delete(key);
        log.warn("redis.unlock(deprecated) key={}, ok={}", key, ok);
        return Boolean.TRUE.equals(ok);
    }

    /* ========================= KV Operations ========================= */

    /**
     * Set value (no expiration).
     *
     * @param key redis key (required)
     * @param val value to set
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public void put(String key, Object val) {
        requireKey(key);
        redisTemplate.opsForValue().set(key, val);
    }

    /**
     * Set value with expiration (seconds).
     *
     * @param key redis key (required)
     * @param val value to set
     * @param seconds expiration in seconds
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public void put(String key, Object val, long seconds) {
        put(key, val, seconds, TimeUnit.SECONDS);
    }

    /**
     * Set value with expiration and time unit.
     *
     * @param key redis key (required)
     * @param val value to set
     * @param expired expiration duration (coerced to {@code >= 0})
     * @param unit time unit
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public void put(String key, Object val, long expired, TimeUnit unit) {
        requireKey(key);
        long ttl = Math.max(0, expired);
        redisTemplate.opsForValue().set(key, val, ttl, unit);
    }

    /**
     * Set value if absent with expiration (seconds).
     *
     * @param key redis key (required)
     * @param val value to set
     * @param seconds expiration in seconds
     * @return {@code true} if the key was set; {@code false} if it already existed
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public boolean putIfAbsent(String key, Object val, long seconds) {
        requireKey(key);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, val, seconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Get value.
     *
     * @param key redis key (required)
     * @return stored value or {@code null} if not found
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    @Nullable
    public Object get(String key) {
        requireKey(key);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Get value and cast to {@link String}.
     *
     * @param key redis key (required)
     * @return string value or {@code null} if not a string / not found
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    @Nullable
    public String getStr(String key) {
        Object v = get(key);
        return (v instanceof String) ? (String) v : null;
    }

    /**
     * Get value and cast to {@link Integer}.
     *
     * @param key redis key (required)
     * @return integer value or {@code null} if not an integer / not found
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    @Nullable
    public Integer getInt(String key) {
        Object v = get(key);
        return (v instanceof Integer) ? (Integer) v : null;
    }

    /**
     * Check key existence.
     *
     * @param key redis key (required)
     * @return {@code true} if key exists; {@code false} otherwise
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public boolean exists(String key) {
        requireKey(key);
        Boolean b = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(b);
    }

    /**
     * Delete a key.
     *
     * @param key redis key (required)
     * @return {@code true} if deleted; {@code false} otherwise
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public boolean remove(String key) {
        requireKey(key);
        Boolean ok = redisTemplate.delete(key);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Delete multiple keys.
     *
     * @param keys collection of keys
     * @return deleted count (0 when {@code keys} is null/empty)
     */
    public long remove(Collection<String> keys) {
        if (keys == null || keys.isEmpty())
            return 0;
        Long n = redisTemplate.delete(keys);
        return n == null ? 0 : n;
    }

    /**
     * Set expiration (seconds).
     *
     * @param key redis key (required)
     * @param seconds expiration in seconds
     * @return {@code true} if set successfully; {@code false} otherwise
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public boolean expire(String key, long seconds) {
        requireKey(key);
        Boolean ok = redisTemplate.expire(key, seconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * Get TTL in seconds.
     *
     * @param key redis key (required)
     * @return TTL in seconds; Redis semantics: {@code -2} = not exist, {@code -1} = no expiration
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public long ttl(String key) {
        requireKey(key);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl == null ? -2 : ttl; // Redis semantics: -2 = not exist; -1 = no expiration
    }

    /**
     * Increment by delta.
     *
     * @param key redis key (required)
     * @param delta increment value
     * @return new value after increment (may be {@code null} if operation failed)
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public Long incrBy(String key, long delta) {
        requireKey(key);
        return redisTemplate.opsForValue().increment(key, delta);
    }

    /**
     * Decrement by delta.
     *
     * @param key redis key (required)
     * @param delta decrement value
     * @return new value after decrement (may be {@code null} if operation failed)
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public Long decrBy(String key, long delta) {
        requireKey(key);
        return redisTemplate.opsForValue().decrement(key, delta);
    }

    /**
     * Batch set multiple key-values.
     *
     * @param kv map of key-values (no-op when {@code null} or empty)
     */
    public void multiSet(Map<String, ?> kv) {
        if (kv == null || kv.isEmpty())
            return;
        redisTemplate.opsForValue().multiSet(kv);
    }

    /**
     * Batch get values for multiple keys.
     *
     * @param keys collection of keys
     * @return list of values (empty list when {@code keys} is null/empty)
     */
    public List<Object> multiGet(Collection<String> keys) {
        if (keys == null || keys.isEmpty())
            return Collections.emptyList();
        return redisTemplate.opsForValue().multiGet(keys);
    }

    /* ========================= Scan / Batch ========================= */

    /**
     * SCAN keys matching a pattern (avoids blocking from {@code KEYS}).
     *
     * @param pattern pattern like {@code "prefix:*"}
     * @return matched key set (never null)
     * @throws IllegalArgumentException if {@code pattern} is null/empty
     */
    public Set<String> scan(String pattern) {
        requirePattern(pattern);
        Set<String> result = new HashSet<>(DEFAULT_SCAN_COUNT);
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(DEFAULT_SCAN_COUNT)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor != null && cursor.hasNext()) {
                result.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("redis.scan pattern={} failed: {}", pattern, e.getMessage());
        }
        return result;
    }

    /**
     * Batch deletion via SCAN (by chunks), avoiding blocking from deleting a large key set at once.
     *
     * @param pattern wildcard pattern
     * @param count SCAN cursor hint per iteration (nullable; default 1000)
     * @return total number of deleted keys
     * @throws IllegalArgumentException if {@code pattern} is null/empty
     */
    public long removeScan(String pattern, @Nullable Integer count) {
        requirePattern(pattern);
        long total = 0L;
        List<String> toDel = new ArrayList<>(BATCH_DELETE_SIZE);

        ScanOptions options = (count == null || count <= 0)
                ? ScanOptions.scanOptions().match(pattern).count(DEFAULT_SCAN_COUNT).build()
                : ScanOptions.scanOptions().match(pattern).count(count).build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor != null && cursor.hasNext()) {
                toDel.add(cursor.next());
                if (toDel.size() >= BATCH_DELETE_SIZE) {
                    total += Optional.ofNullable(redisTemplate.delete(toDel)).orElse(0L);
                    toDel.clear();
                }
            }
        } catch (Exception e) {
            log.warn("redis.removeScan pattern={} failed: {}", pattern, e.getMessage());
        }

        if (!toDel.isEmpty()) {
            total += Optional.ofNullable(redisTemplate.delete(toDel)).orElse(0L);
        }
        log.info("redis.removeScan pattern={}, removedKeys={}", pattern, total);
        return total;
    }

    /**
     * (Not recommended) {@code KEYS}-based matching, which may block Redis.
     *
     * @param pattern pattern
     * @return matched key set
     * @throws IllegalArgumentException if {@code pattern} is null/empty
     */
    @Deprecated
    public Set<String> getPatternKeys(String pattern) {
        requirePattern(pattern);
        return redisTemplate.keys(pattern);
    }

    /**
     * Batch read values whose keys match the pattern.
     *
     * @param pattern pattern
     * @return list of values (empty list when no keys matched)
     * @throws IllegalArgumentException if {@code pattern} is null/empty
     */
    public List<Object> getLikeList(String pattern) {
        Set<String> keys = scan(pattern);
        if (keys.isEmpty())
            return Collections.emptyList();
        return redisTemplate.opsForValue().multiGet(keys);
    }

    /* ========================= Set Operations ========================= */

    /**
     * Add members to a set.
     *
     * @param key redis key (required)
     * @param values members to add
     * @return number of elements that were added (may be {@code null} on failure)
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public Long sadd(String key, String... values) {
        requireKey(key);
        return redisTemplate.opsForSet().add(key, (Object[]) values);
    }

    /**
     * Remove members from a set.
     *
     * @param key redis key (required)
     * @param values members to remove
     * @return number of elements removed (may be {@code null} on failure)
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public Long srem(String key, String... values) {
        requireKey(key);
        return redisTemplate.opsForSet().remove(key, (Object[]) values);
    }

    /**
     * Get set cardinality.
     *
     * @param key redis key (required)
     * @return size (may be {@code null} on failure)
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public Long scard(String key) {
        requireKey(key);
        return redisTemplate.opsForSet().size(key);
    }

    /**
     * Check whether a member is in the set.
     *
     * @param key redis key (required)
     * @param value member value
     * @return {@code true} if a member; {@code false} otherwise (may be {@code null} on failure)
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public Boolean sismember(String key, String value) {
        requireKey(key);
        return redisTemplate.opsForSet().isMember(key, value);
    }

    /**
     * Get all members of a set.
     *
     * @param key redis key (required)
     * @return members set (may be {@code null} on failure)
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public Set<Object> smembers(String key) {
        requireKey(key);
        return redisTemplate.opsForSet().members(key);
    }

    /* ========================= Hash Operations ========================= */

    /**
     * Put a hash field.
     *
     * @param key redis key (required)
     * @param field hash field (required)
     * @param value value
     * @throws IllegalArgumentException if {@code key} is null/empty or {@code field} is null
     */
    public void hset(String key, String field, Object value) {
        requireKey(key);
        Objects.requireNonNull(field, "field must not be null");
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * Delete one or more hash fields.
     *
     * @param key redis key (required)
     * @param fields fields to delete
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public void hdel(String key, String... fields) {
        requireKey(key);
        redisTemplate.opsForHash().delete(key, (Object[]) fields);
    }

    /**
     * Get all hash entries.
     *
     * @param key redis key (required)
     * @return map of entries (never null)
     * @throws IllegalArgumentException if {@code key} is null/empty
     */
    public Map<Object, Object> hgetAll(String key) {
        requireKey(key);
        return redisTemplate.opsForHash().entries(key);
    }

    /* ========================= Internal Validation / Small Helpers ========================= */

    /** Validate key: must be non-null and non-empty. */
    private static void requireKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("redis key must not be null/empty");
        }
    }

    /** Validate pattern: must be non-null and non-empty. */
    private static void requirePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern must not be null/empty");
        }
    }

    /** Mask most characters of a token in logs to avoid leakage. */
    private static String safe(String token) {
        if (token == null)
            return "null";
        byte[] b = token.getBytes(StandardCharsets.UTF_8);
        if (b.length <= 4)
            return "***";
        return "***" + token.substring(token.length() - 4);
    }
}
