package com.ppaass.proxy;

import com.ppaass.common.log.PpaassLoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Launcher {
    public static void main(String[] args) {
        PpaassLoggerFactory.INSTANCE.init("com.ppaass.proxy.ProxyPpaassLogger");
        var logger = PpaassLoggerFactory.INSTANCE.getLogger();
        var context = SpringApplication.run(Launcher.class, args);
        var proxy = context.getBean(Proxy.class);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info(() -> "Begin to stop proxy...");
            proxy.stop();
            logger.info(() -> "Proxy stopped...");
        }));
        logger.info(() -> "Begin to start proxy [id = {}] ...",
                () -> new Object[]{proxy.getInstanceId()});
        proxy.start();
        logger
                .info(() -> "Proxy started [id = {}]...", () -> new Object[]{proxy.getInstanceId()});
    }
}
