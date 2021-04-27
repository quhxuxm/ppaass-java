package com.ppaass.common.log;

import com.ppaass.common.exception.PpaassException;

public class PpaassLoggerFactory {
    public static final PpaassLoggerFactory INSTANCE = new PpaassLoggerFactory();
    private IPpaassLogger logger;

    private PpaassLoggerFactory() {
    }

    public void init(Class<? extends IPpaassLogger> loggerClass) {
        try {
            this.logger = loggerClass.getConstructor().newInstance();
        } catch (Exception e) {
            throw new PpaassException("Fail to initialize logger factory because of exception.", e);
        }
    }

    public void init(String className) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends IPpaassLogger> loggerClass = (Class<? extends IPpaassLogger>) Class.forName(className);
            this.init(loggerClass);
        } catch (Exception e) {
            throw new PpaassException("Fail to initialize logger factory because of exception.", e);
        }
    }

    public IPpaassLogger getLogger() {
        return this.logger;
    }
}
