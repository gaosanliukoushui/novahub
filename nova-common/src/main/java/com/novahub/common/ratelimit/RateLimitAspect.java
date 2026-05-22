package com.novahub.common.ratelimit;

import com.novahub.common.annotation.RateLimitBySlideWindow;
import com.novahub.common.annotation.RateLimitByTokenBucket;
import com.novahub.common.exception.RateLimitExceededException;
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

/**
 * 限流切面。
 * 拦截标注了 @RateLimitBySlideWindow 或 @RateLimitByTokenBucket 的方法，
 * 根据注解参数执行对应的限流策略，超限时抛出 RateLimitExceededException。
 */
@Aspect
@Component
@Order(1)
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final SlidingWindowRateLimiter slidingWindowRateLimiter;
    private final TokenBucketRateLimiter tokenBucketRateLimiter;
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    public RateLimitAspect(SlidingWindowRateLimiter slidingWindowRateLimiter,
                           TokenBucketRateLimiter tokenBucketRateLimiter) {
        this.slidingWindowRateLimiter = slidingWindowRateLimiter;
        this.tokenBucketRateLimiter = tokenBucketRateLimiter;
    }

    @Around("@annotation(rateLimitBySlideWindow)")
    public Object aroundSlideWindow(ProceedingJoinPoint joinPoint, RateLimitBySlideWindow rateLimitBySlideWindow) throws Throwable {
        String key = resolveKey(rateLimitBySlideWindow.key(), joinPoint);
        long windowSize = rateLimitBySlideWindow.windowSizeSeconds();
        long maxRequests = rateLimitBySlideWindow.maxRequests();

        if (!slidingWindowRateLimiter.tryAcquire(key, windowSize, maxRequests)) {
            log.warn("滑动窗口限流触发: key={}, window={}s, max={}", key, windowSize, maxRequests);
            throw new RateLimitExceededException();
        }

        return joinPoint.proceed();
    }

    @Around("@annotation(rateLimitByTokenBucket)")
    public Object aroundTokenBucket(ProceedingJoinPoint joinPoint, RateLimitByTokenBucket rateLimitByTokenBucket) throws Throwable {
        String key = resolveKey(rateLimitByTokenBucket.key(), joinPoint);
        long capacity = rateLimitByTokenBucket.capacity();
        long refillTokens = rateLimitByTokenBucket.refillTokens();
        long refillDuration = rateLimitByTokenBucket.refillDurationSeconds();

        if (!tokenBucketRateLimiter.tryAcquire(key, capacity, refillTokens, refillDuration)) {
            log.warn("令牌桶限流触发: key={}, capacity={}, refill={}/{}s",
                    key, capacity, refillTokens, refillDuration);
            throw new RateLimitExceededException();
        }

        return joinPoint.proceed();
    }

    private String resolveKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        if (keyExpression == null || keyExpression.isBlank()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return signature.getDeclaringTypeName() + "." + signature.getName();
        }

        if (!keyExpression.contains("#{")) {
            return keyExpression;
        }

        String[] paramNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();

        EvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                ((StandardEvaluationContext) context).setVariable(paramNames[i], args[i]);
            }
        }

        Expression expression = expressionParser.parseExpression(keyExpression);
        Object value = expression.getValue(context);
        return value == null ? keyExpression : value.toString();
    }
}
