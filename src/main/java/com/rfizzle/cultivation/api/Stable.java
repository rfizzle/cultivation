package com.rfizzle.cultivation.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as part of Cultivation's stable public API surface (Concord API
 * Standard): stable across patch and minor versions, breaking changes only with
 * a major version bump and a changelog entry. Everything outside the
 * {@code com.rfizzle.cultivation.api} package is internal and may change
 * without notice in any release.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Stable {
}
