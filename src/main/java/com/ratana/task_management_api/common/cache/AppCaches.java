package com.ratana.task_management_api.common.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ratana.task_management_api.organization.dto.response.DepartmentResponse;
import com.ratana.task_management_api.organization.dto.response.PositionResponse;
import com.ratana.task_management_api.rbac.dto.response.PermissionResponse;
import com.ratana.task_management_api.rbac.dto.response.RoleResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Every Caffeine cache in the app, each used cache-aside through {@link CacheAside}.
 *
 * <p>Caches are per instance. With several API instances an invalidation on one doesn't reach the
 * others, so each entry also expires on its own: the TTLs bound how stale another instance can be.
 *
 * <p>Hit/miss/eviction counts are published to Micrometer as {@code cache.gets{cache=...}} etc.,
 * visible at /management/metrics (ADMIN).
 */
@Component
public class AppCaches {

    /** Key for caches that hold a single "whole list" entry. */
    public static final String ALL = "all";

    private final CacheAside<String, UserDetails> principals;
    private final CacheAside<String, List<PermissionResponse>> permissions;
    private final CacheAside<String, List<RoleResponse>> roles;
    private final CacheAside<String, List<PositionResponse>> positions;
    private final CacheAside<String, List<DepartmentResponse>> departments;

    public AppCaches(MeterRegistry meterRegistry,
                     @Value("${app.cache.principal-ttl:60s}") Duration principalTtl,
                     @Value("${app.cache.reference-ttl:10m}") Duration referenceTtl) {
        // Short TTL: a role or status change made on another instance (or in SQL) applies within it.
        this.principals = create(meterRegistry, "authPrincipals", principalTtl, 10_000);
        this.permissions = create(meterRegistry, "permissions", referenceTtl, 1);
        this.roles = create(meterRegistry, "roles", referenceTtl, 1);
        this.positions = create(meterRegistry, "positions", referenceTtl, 1);
        this.departments = create(meterRegistry, "departments", referenceTtl, 1);
    }

    /** UserPrincipal by email, read on every authenticated request (JwtAuthenticationFilter). */
    public CacheAside<String, UserDetails> principals() {
        return principals;
    }

    /** GET /permissions. */
    public CacheAside<String, List<PermissionResponse>> permissions() {
        return permissions;
    }

    /** GET /roles: each role carries its permissions. */
    public CacheAside<String, List<RoleResponse>> roles() {
        return roles;
    }

    /** GET /positions: each position carries its department's name. */
    public CacheAside<String, List<PositionResponse>> positions() {
        return positions;
    }

    /** GET /departments: each department carries its manager's name and avatar. */
    public CacheAside<String, List<DepartmentResponse>> departments() {
        return departments;
    }

    private static <V> CacheAside<String, V> create(MeterRegistry registry, String name,
                                                   Duration ttl, long maxEntries) {
        CacheAside<String, V> cache = new CacheAside<>(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxEntries)
                .recordStats()
                .build());
        CaffeineCacheMetrics.monitor(registry, cache.nativeCache(), name);
        return cache;
    }
}
