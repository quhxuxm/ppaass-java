package com.ppaass.proxy;

import com.ppaass.common.log.PpaassLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Launcher {
    static {
        PpaassLogger.INSTANCE.register(Launcher.class);
    }

    public static void main(String[] args) {
        var context = SpringApplication.run(Launcher.class, args);
        var proxy = context.getBean(Proxy.class);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PpaassLogger.INSTANCE.info(Launcher.class, () -> "Begin to stop proxy...");
            proxy.stop();
            PpaassLogger.INSTANCE.info(Launcher.class, () -> "Proxy stopped...");
        }));
        PpaassLogger.INSTANCE.info(Launcher.class, () -> "Begin to start proxy...");
        proxy.start();
        PpaassLogger.INSTANCE.info(Launcher.class, () -> "Proxy started...");
    }
}
