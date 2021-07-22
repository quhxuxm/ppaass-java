package com.ppaass.agent;

import com.ppaass.agent.ui.MainFrame;
import com.ppaass.common.log.PpaassLoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.awt.*;

/**
 * The launcher of the agent.
 */
@SpringBootApplication
public class Launcher {
    public static void main(String[] args) {
        PpaassLoggerFactory.INSTANCE.init(AgentPpaassLogger.class);
        var logger = PpaassLoggerFactory.INSTANCE.getLogger();
        var context =
                new SpringApplicationBuilder(Launcher.class)
                        .headless(false).run(args);
        var agentConfiguration = context.getBean(AgentConfiguration.class);
        if (agentConfiguration.isWithUi()) {
            logger.info(() -> "Ppaass agent is starting with UI...");
            EventQueue.invokeLater(() -> {
                var mainFrame = context.getBean(MainFrame.class);
                mainFrame.start();
            });
            return;
        }
        logger.info(() -> "Ppaass agent is starting without UI...");
        var agent = context.getBean(Agent.class);
        Runtime.getRuntime().addShutdownHook(new Thread(agent::stop));
        agent.start();
    }
}
