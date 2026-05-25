package com.novahub.common.idempotent;

import com.novahub.common.annotation.Idempotent;
import com.novahub.common.annotation.IdempotentType;
import com.novahub.common.exception.DuplicateRequestException;
import com.novahub.common.utils.RedisUtils;
import com.novahub.common.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * 幂等性切面。
 * 拦截标注了 @Idempotent 的方法，防止接口重复提交。
 * 支持 LOCK（分布式锁）和 TOKEN（令牌验证）两种模式。
 */
@Aspect
@Component
@Order(2)
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private static final String IDEMPOTENT_LOCK_PREFIX = "idempotent:lock:";
    private static final String IDEMPOTENT_TOKEN_PREFIX = "idempotent:token:";

    private final RedisUtils redisUtils;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public IdempotentAspect(RedisUtils redisUtils) {
        this.redisUtils = redisUtils;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String key = resolveKey(idempotent.key(), joinPoint);
        long expireSeconds = idempotent.expireSeconds();

        if (idempotent.type() == IdempotentType.LOCK) {
            return handleLockMode(key, expireSeconds, joinPoint);
        } else {
            return handleTokenMode(key, expireSeconds, idempotent.tokenHeader(), joinPoint);
        }
    }

    /**
     * LOCK 模式：使用 Redis setIfAbsent 原子加锁。
     * 成功加锁则执行业务，完成后不主动删除锁（依赖过期时间）。
     * 加锁失败则抛出 DuplicateRequestException。
     */
    private Object handleLockMode(String key, long expireSeconds, ProceedingJoinPoint joinPoint) throws Throwable {
        String lockKey = IDEMPOTENT_LOCK_PREFIX + key;
        Boolean acquired = redisUtils.setIfAbsent(lockKey, "1", expireSeconds, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("幂等锁触发（重复提交）: key={}", key);
            throw new DuplicateRequestException();
        }

        try {
            return joinPoint.proceed();
        } finally {
            // 可选：业务执行成功后主动删除锁，避免锁长期占用 key 空间
            // redisUtils.delete(lockKey);
        }
    }

    /**
     * TOKEN 模式：从请求 Header 获取 token，先查再删，保证只生效一次。
     * 适用于前端防重复提交场景，前端生成唯一 token（如 UUID）放在 Header 中。
     */
    private Object handleTokenMode(String key, long expireSeconds, String tokenHeader,
                                   ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        String token = request.getHeader(tokenHeader);

        if (token == null || token.isBlank()) {
            return joinPoint.proceed();
        }

        String tokenKey = IDEMPOTENT_TOKEN_PREFIX + key + ":" + token;

        // 先查 token 是否存在（GET），不存在则放行并写入（SET）
        // 使用 setIfAbsent 实现"查+写"的原子性：首次请求成功写入，返回 true
        // 重复请求时 key 已存在，写入失败，返回 false
        Boolean acquired = redisUtils.setIfAbsent(tokenKey, "1", expireSeconds, TimeUnit.SECONDS);

        if (Boolean.FALSE.equals(acquired)) {
            log.warn("幂等Token触发（重复提交）: key={}, token={}", key, token);
            throw new DuplicateRequestException();
        }

        return joinPoint.proceed();
    }

    private String resolveKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        if (keyExpression == null || keyExpression.isBlank()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return signature.getDeclaringTypeName() + "." + signature.getName();
        }

        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();

        EvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                ((StandardEvaluationContext) context).setVariable(paramNames[i], args[i]);
            }
        }

        try {
            Expression expression = expressionParser.parseExpression(keyExpression);
            Object value = expression.getValue(context);
            String resolved = value == null ? keyExpression : value.toString();
            return appendUserId(resolved);
        } catch (Exception e) {
            log.debug("SpEL expression evaluation failed, using raw key: {}", keyExpression, e);
            return appendUserId(keyExpression);
        }
    }

    private String appendUserId(String resolvedKey) {
        Long userId = SecurityUtils.getUserId();
        if (userId != null) {
            return resolvedKey + ":" + userId;
        }
        return resolvedKey;
    }
}
