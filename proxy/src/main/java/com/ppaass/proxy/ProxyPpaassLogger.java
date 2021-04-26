package com.ppaass.proxy;

import com.ppaass.common.log.IPpaassLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public class ProxyPpaassLogger implements IPpaassLogger {
    private static final ConcurrentMap<String, Logger> loggers = new ConcurrentHashMap<>();

    public ProxyPpaassLogger() {
    }

    private String findCurrentLoggerClassStackTraceIndex(StackTraceElement[] stackTraceElements) {
        for (int i = 0; i < stackTraceElements.length; i++) {
            if (stackTraceElements[i].getClassName().equals(ProxyPpaassLogger.class.getName())) {
                return stackTraceElements[i + 1].getClassName();
            }
        }
        return ProxyPpaassLogger.class.getName();
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

    public void info(Supplier<String> logSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isInfoEnabled()) {
            logger.info(logSupplier.get());
        }
    }

    public void info(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
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

    public void debug(Supplier<String> logSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isDebugEnabled()) {
            logger.debug(logSupplier.get());
        }
    }

    public void debug(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
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

    public void warn(Supplier<String> logSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isWarnEnabled()) {
            logger.warn(logSupplier.get());
        }
    }

    public void warn(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
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

    public void error(Supplier<String> logSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isErrorEnabled()) {
            logger.error(logSupplier.get());
        }
    }

    public void error(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
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

    public void trace(Supplier<String> logSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get());
        }
    }

    public void trace(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier) {
        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
        String invokingClassName = this.findCurrentLoggerClassStackTraceIndex(stackTraceElements);
        Logger logger = loggers.computeIfAbsent(invokingClassName, LoggerFactory::getLogger);
        if (logger.isTraceEnabled()) {
            logger.trace(logSupplier.get(), argumentsSupplier.get());
        }
    }
}
