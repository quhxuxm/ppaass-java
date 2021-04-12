package com.ppaass.common.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PpaassLogger {
    public static PpaassLogger INSTANCE = new PpaassLogger();
    private static final Map<Class<?>, Logger> loggers = new HashMap<>();

    private PpaassLogger() {
    }

    public void register(Class<?> clazz) {
        Logger logger = LoggerFactory.getLogger(clazz);
        loggers.put(clazz, logger);
    }

    public <T> void info(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isInfoEnabled()) {
            logger.info(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void info(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isInfoEnabled()) {
            logger.info(logSupplier.get());
        }
    }

    public <T> void debug(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isDebugEnabled()) {
            logger.debug(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void debug(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isDebugEnabled()) {
            logger.debug(logSupplier.get());
        }
    }

    public <T> void warn(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isWarnEnabled()) {
            logger.warn(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void warn(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isWarnEnabled()) {
            logger.warn(logSupplier.get());
        }
    }

    public <T> void error(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isErrorEnabled()) {
            logger.error(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void error(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isErrorEnabled()) {
            logger.error(logSupplier.get());
        }
    }

    public <T> void trace(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void trace(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.get(targetClass);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get());
        }
    }
}
