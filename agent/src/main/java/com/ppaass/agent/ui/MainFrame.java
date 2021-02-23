package com.ppaass.agent.ui;

import javax.swing.*;

public class MainFrame extends JFrame {
    private static final String TOKEN_LABEL_MESSAGE_KEY = "mainFrame.tokenLabel";
    private static final String AGENT_TCP_PORT_LABEL_MESSAGE_KEY = "mainFrame.agentTcpPortLabel";
    private static final String PROXY_ADDRESS_LABEL_MESSAGE_KEY = "mainFrame.proxyAddressLabel";
    private static final String PROXY_PORT_LABEL_MESSAGE_KEY = "mainFrame.proxyPortLabel";
    private static final String SYSTEM_TRAY_TOOLTIP_MESSAGE_KEY = "mainFrame.systemTray.tooltip";
    private static final String STATUS_LABEL_DEFAULT_MESSAGE_KEY = "mainFrame.statusLabel.default";
    private static final String BUTTON_START_PROXY_MESSAGE_KEY = "mainFrame.button.startProxy";
    private static final String BUTTON_ADJUST_LOGGER_MESSAGE_KEY = "mainFrame.button.adjustLogger";
    private static final String DIALOG_ADJUST_LOGGER_TITLE_MESSAGE_KEY = "mainFrame.dialog.adjustLogger.title";
    private static final String BUTTON_STOP_PROXY_MESSAGE_KEY = "mainFrame.button.stopProxy";
    private static final String STATUS_TOKEN_VALIDATION_FAIL_MESSAGE_KEY =
            "mainFrame.status.tokenValidationFail";
    private static final String STATUS_PORT_VALIDATION_FAIL_MESSAGE_KEY = "mainFrame.status.portValidationFail";
    private static final String STATUS_PROXY_PORT_VALIDATION_FAIL_MESSAGE_KEY =
            "mainFrame.status.proxyPortValidationFail";
    private static final String STATUS_PROXY_IS_RUNNING_MESSAGE_KEY = "mainFrame.status.proxyIsRunning";
    private static final String STATUS_AGENT_START_FAIL_MESSAGE_KEY = "mainFrame.status.agentStartFail";
    private static final String LOGO_BLACK = "icons/logo_black.png";
    private static final String LOGO_WHITE = "icons/logo_white.png";
    private static final int PANEL_WIDTH = 500;

    public void start() {
        this.setVisible(true);
    }

    public void stop() {
        this.setVisible(false);
    }
}
