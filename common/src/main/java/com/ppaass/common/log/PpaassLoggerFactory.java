package com.ppaass.common.log;

import com.ppaass.common.exception.PpaassException;

public class PpaassLoggerFactory {
    public static final PpaassLoggerFactory INSTANCE = new PpaassLoggerFactory();
    private IPpaassLogger logger;

    private PpaassLoggerFactory() {
    }

    public void init(String className) {
        try {
            Class<?> loggerClass = Class.forName(className);
            this.logger = (IPpaassLogger) loggerClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new PpaassException("Fail to initialize logger factory because of exception.", e);
        }
    }

    public IPpaassLogger getLogger() {
        return this.logger;
    }
}
