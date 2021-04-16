package com.ppaass.common.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class PpaassLogger {
    public static PpaassLogger INSTANCE = new PpaassLogger();
    private static final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();
    private static final int STACK_TRACE_INDEX_FOR_LOGGING = 2;

    private PpaassLogger() {
    }

    public <T> void info(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isInfoEnabled()) {
            logger.info(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void info(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isInfoEnabled()) {
            logger.info(logSupplier.get());
        }
    }

    public <T> void info(Supplier<String> logSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isInfoEnabled()) {
            logger.info(logSupplier.get());
        }
    }

    public <T> void info(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isInfoEnabled()) {
            logger.info(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void debug(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isDebugEnabled()) {
            logger.debug(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void debug(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isDebugEnabled()) {
            logger.debug(logSupplier.get());
        }
    }

    public <T> void debug(Supplier<String> logSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isDebugEnabled()) {
            logger.debug(logSupplier.get());
        }
    }

    public <T> void debug(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isDebugEnabled()) {
            logger.debug(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void warn(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isWarnEnabled()) {
            logger.warn(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void warn(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isWarnEnabled()) {
            logger.warn(logSupplier.get());
        }
    }

    public <T> void warn(Supplier<String> logSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isWarnEnabled()) {
            logger.warn(logSupplier.get());
        }
    }

    public <T> void warn(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isWarnEnabled()) {
            logger.warn(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void error(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isErrorEnabled()) {
            logger.error(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void error(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isErrorEnabled()) {
            logger.error(logSupplier.get());
        }
    }

    public <T> void error(Supplier<String> logSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isErrorEnabled()) {
            logger.error(logSupplier.get());
        }
    }

    public <T> void error(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isErrorEnabled()) {
            logger.error(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void trace(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get(), argumentsSupplier.get());
        }
    }

    public <T> void trace(Class<T> targetClass, Supplier<String> logSupplier) {
        Logger logger = loggers.computeIfAbsent(targetClass.getName(), LoggerFactory::getLogger);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get());
        }
    }

    public <T> void trace(Supplier<String> logSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get());
        }
    }

    public <T> void trace(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        String invokingClassName = Thread.currentThread().getStackTrace()[STACK_TRACE_INDEX_FOR_LOGGING].getClassName();
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get(), argumentsSupplier.get());
        }
    }
}
