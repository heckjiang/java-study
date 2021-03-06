package com.tuacy.study.distributelock.distributedlock.db;

import com.tuacy.study.distributelock.config.DbDistributedLockConfiguration;
import com.tuacy.study.distributelock.distributedlock.LockFailAction;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @name: DistributedLockAspectConfiguration
 * @author: tuacy.
 * @date: 2019/8/6.
 * @version: 1.0
 * @Description:
 */
@Aspect
@Configuration
@ConditionalOnClass(IDbDistributedLock.class)
@AutoConfigureAfter(DbDistributedLockConfiguration.class)
public class DbDistributedLockAspect {

    private final Logger logger = LoggerFactory.getLogger(DbDistributedLockAspect.class);

    private IDbDistributedLock dbDistributedLock;

    @Autowired
    public void setDbDistributedLock(IDbDistributedLock dbDistributedLock) {
        this.dbDistributedLock = dbDistributedLock;
    }

    @Pointcut("@annotation(com.tuacy.study.distributelock.distributedlock.db.DbDistributedLock)")
    private void lockPoint() {

    }

    @Around("lockPoint()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        DbDistributedLock dbDistributedLockAnnotation = method.getAnnotation(DbDistributedLock.class);
        String key = dbDistributedLockAnnotation.key();
        if (StringUtils.isEmpty(key)) {
            Object[] args = pjp.getArgs();
            key = Arrays.toString(args);
        }
        int retryTimes = dbDistributedLockAnnotation.action().equals(LockFailAction.CONTINUE) ? dbDistributedLockAnnotation.retryTimes() : 0;
        boolean lock = dbDistributedLock.lock(key, retryTimes, dbDistributedLockAnnotation.sleepMills());
        if (!lock) {
            logger.debug("get lock failed : " + key);
            return null;
        }

        //得到锁,执行方法，释放锁
        logger.debug("get lock success : " + key);
        try {
            return pjp.proceed();
        } catch (Exception e) {
            logger.error("execute locked method occured an exception", e);
        } finally {
            dbDistributedLock.unlock(key);
            logger.debug("release lock");
        }
        return null;
    }
}
