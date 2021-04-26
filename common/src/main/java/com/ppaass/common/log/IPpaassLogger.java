package com.ppaass.common.log;

import java.util.function.Supplier;

public interface IPpaassLogger {
    <T> void info(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void info(Class<T> targetClass, Supplier<String> logSupplier);

    void info(Supplier<String> logSupplier);

    void info(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void debug(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void debug(Class<T> targetClass, Supplier<String> logSupplier);

    void debug(Supplier<String> logSupplier);

    void debug(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void warn(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void warn(Class<T> targetClass, Supplier<String> logSupplier);

    void warn(Supplier<String> logSupplier);

    void warn(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void error(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void error(Class<T> targetClass, Supplier<String> logSupplier);

    void error(Supplier<String> logSupplier);

    void error(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void trace(Class<T> targetClass, Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);

    <T> void trace(Class<T> targetClass, Supplier<String> logSupplier);

    void trace(Supplier<String> logSupplier);

    void trace(Supplier<String> logSupplier, Supplier<Object[]> argumentsSupplier);
}
