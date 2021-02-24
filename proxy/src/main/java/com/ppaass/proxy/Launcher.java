package com.ppaass.proxy;

import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Launcher {
    public static void main(String[] args) {
        var logger = LoggerFactory.getLogger(Launcher.class);
        var context = SpringApplication.run(Launcher.class, args);
        var proxy = context.getBean(Proxy.class);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Begin to stop proxy...");
            proxy.stop();
            logger.info("Proxy stopped...");
        }));
        logger.info("Begin to start proxy...");
        proxy.start();
        logger.info("Proxy started...");
    }
}
