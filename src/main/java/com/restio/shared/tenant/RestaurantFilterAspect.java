package com.restio.shared.tenant;

import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import com.restio.shared.domain.RestaurantScopedEntity;

/**
 * Keeps the restaurant isolation filter enabled. Every repository call re-applies it to the current
 * Hibernate session with the restaurants of {@link RestaurantContext}, except inside a {@link
 * SystemContext} method, where the filter is disabled on purpose.
 *
 * <p>The filter lives in the Hibernate session, so repository access must happen inside a
 * transaction (the {@code application} layer is always {@code @Transactional}); outside one, each
 * call would open its own session and the filter would not apply.
 */
@Aspect
@Component
public class RestaurantFilterAspect {

    @PersistenceContext private EntityManager entityManager;

    @Around("@annotation(com.restio.shared.tenant.SystemContext)")
    public Object aroundSystemContext(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean previous = RestaurantContext.isSystem();
        RestaurantContext.setSystem(true);
        try {
            return joinPoint.proceed();
        } finally {
            RestaurantContext.setSystem(previous);
        }
    }

    @Before("execution(* org.springframework.data.repository.Repository+.*(..))")
    public void beforeRepositoryCall() {
        Session session = entityManager.unwrap(Session.class);

        if (RestaurantContext.isSystem()) {
            session.disableFilter(RestaurantScopedEntity.FILTER_NAME);
            return;
        }

        Filter filter = session.getEnabledFilter(RestaurantScopedEntity.FILTER_NAME);
        if (filter == null) {
            filter = session.enableFilter(RestaurantScopedEntity.FILTER_NAME);
        }
        filter.setParameterList(
                RestaurantScopedEntity.FILTER_PARAM, RestaurantContext.allowedRestaurantIds());
    }

    /**
     * {@code findById} loads through {@code EntityManager.find}, which Hibernate filters ignore, so
     * the restaurant is checked on the loaded entity instead.
     */
    @Around(
            "execution(java.util.Optional org.springframework.data.repository.Repository+.findById(..))")
    public Object aroundFindById(ProceedingJoinPoint joinPoint) throws Throwable {
        Optional<?> result = (Optional<?>) joinPoint.proceed();
        if (RestaurantContext.isSystem()) {
            return result;
        }
        return result.filter(
                entity ->
                        !(entity instanceof RestaurantScopedEntity scoped)
                                || RestaurantContext.allowedRestaurantIds()
                                        .contains(scoped.getRestaurantId()));
    }
}
