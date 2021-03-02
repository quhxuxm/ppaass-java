package com.ppaass.common.log;

import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class PpaassLogger {
    public static PpaassLogger INSTANCE = new PpaassLogger();

    private PpaassLogger() {
    }

    public void register(Class<?> clazz) {
        LoggerFactory.getLogger(clazz);
    }

    public <T> void info(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        var logger = LoggerFactory.getLogger(targetClass);
        if (logger.isInfoEnabled()) {
            logger.info(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void debug(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        var logger = LoggerFactory.getLogger(targetClass);
        if (logger.isDebugEnabled()) {
            logger.debug(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void warn(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        var logger = LoggerFactory.getLogger(targetClass);
        if (logger.isWarnEnabled()) {
            logger.warn(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void error(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        var logger = LoggerFactory.getLogger(targetClass);
        if (logger.isErrorEnabled()) {
            logger.error(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void trace(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        var logger = LoggerFactory.getLogger(targetClass);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get(), argumentsSupplier.get());
        }
    }
}
