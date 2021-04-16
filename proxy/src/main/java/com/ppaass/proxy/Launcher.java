package com.ppaass.proxy;

import com.ppaass.common.log.PpaassLogger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Launcher {
    public static void main(String[] args) {
        var context = SpringApplication.run(Launcher.class, args);
        var proxy = context.getBean(Proxy.class);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            PpaassLogger.INSTANCE.info(() -> "Begin to stop proxy...");
            proxy.stop();
            PpaassLogger.INSTANCE.info(() -> "Proxy stopped...");
        }));
        PpaassLogger.INSTANCE.info(() -> "Begin to start proxy [id = {}] ...",
                () -> new Object[]{proxy.getInstanceId()});
        proxy.start();
        PpaassLogger.INSTANCE
                .info(() -> "Proxy started [id = {}]...", () -> new Object[]{proxy.getInstanceId()});
    }
}
