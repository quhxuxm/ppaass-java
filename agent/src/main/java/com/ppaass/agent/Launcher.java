package com.ppaass.agent;

import com.ppaass.agent.ui.MainFrame;
import com.ppaass.common.log.PpaassLogger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.awt.*;

@SpringBootApplication
public class Launcher {
    static {
        PpaassLogger.INSTANCE.register(Launcher.class);
    }

    public static void main(String[] args) {
        var context =
                new SpringApplicationBuilder(Launcher.class)
                        .headless(false).run(args);
        EventQueue.invokeLater(() -> {
            var mainFrame = context.getBean(MainFrame.class);
            mainFrame.start();
        });
    }
}
