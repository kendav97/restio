package com.restio.shared.tenant;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Runs the annotated method across every restaurant, with the isolation filter disabled. Reserved
 * for system jobs (synchronisation, scheduled reports, migrations); never for anything reachable
 * from a user request.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SystemContext {}
