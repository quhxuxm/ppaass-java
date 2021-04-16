package com.ppaass.agent.ui;

import com.ppaass.agent.Agent;
import com.ppaass.agent.AgentConfiguration;
import com.ppaass.protocol.common.util.UUIDUtil;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.spi.AbstractLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Comparator;
import java.util.Locale;

@Service
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
    private static final Logger logger = LoggerFactory.getLogger(MainFrame.class);
    private final Agent agent;
    private final MessageSource messageSource;
    private final AgentConfiguration agentConfiguration;

    public MainFrame(Agent agent, MessageSource messageSource, AgentConfiguration agentConfiguration) {
        this.agent = agent;
        this.messageSource = messageSource;
        this.agentConfiguration = agentConfiguration;
    }

    private void initialize() {
        var contentPanel = initializeContent();
        this.setContentPane(contentPanel);
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                stop();
            }
        });
        this.setIconImage(Toolkit.getDefaultToolkit()
                .getImage(MainFrame.class.getClassLoader().getResource(LOGO_BLACK)));
        this.addWindowStateListener(e -> {
                    if (e.getNewState() == ICONIFIED || e.getNewState() == 7) {
                        this.setVisible(false);
                    }
                }
        );
        if (SystemTray.isSupported()) {
            var tray = SystemTray.getSystemTray();
            var image = Toolkit.getDefaultToolkit()
                    .getImage(MainFrame.class.getClassLoader().getResource(LOGO_WHITE));
            var trayIcon = new TrayIcon(image,
                    getMessage(SYSTEM_TRAY_TOOLTIP_MESSAGE_KEY));
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        MainFrame.this.setVisible(!MainFrame.this.isShowing());
                        MainFrame.this.setExtendedState(NORMAL);
                        toFront();
                        return;
                    }
                    System.exit(0);
                }
            });
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {
                logger.error("Fail to add system tray icon because of exception.", e);
            }
        }
        this.setResizable(false);
        pack();
    }

    private boolean preVerifyToken(JTextArea tokenInput) {
        var inputToken = tokenInput.getText();
        return !StringUtils.isEmpty(inputToken);
    }

    private JPanel initializeContent() {
        var contentPanel = new JPanel();
        var contentPanelLayout = new BoxLayout(contentPanel, BoxLayout.Y_AXIS);
        contentPanel.setLayout(contentPanelLayout);
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        var tokenLabelPanel = new JPanel();
        tokenLabelPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 30));
        tokenLabelPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        tokenLabelPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        var tokenLabel = new JLabel(this.getMessage(TOKEN_LABEL_MESSAGE_KEY));
        tokenLabelPanel.add(tokenLabel);
        contentPanel.add(tokenLabelPanel);
        var tokenTextFieldPanel = new JPanel();
        tokenTextFieldPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 100));
        tokenTextFieldPanel.setLayout(new BoxLayout(tokenTextFieldPanel, BoxLayout.Y_AXIS));
        tokenTextFieldPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        var tokenInput = new JTextArea();
        tokenInput.setText(this.agentConfiguration.getUserToken());
        tokenInput.setLineWrap(true);
        tokenInput.setDisabledTextColor(new Color(200, 200, 200));
        var tokenInputScrollPane = new JScrollPane(tokenInput);
        tokenInputScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        tokenInputScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        tokenTextFieldPanel.add(tokenInputScrollPane);
        contentPanel.add(tokenTextFieldPanel);
        var agentTcpPortLabelPanel = new JPanel();
        agentTcpPortLabelPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 30));
        agentTcpPortLabelPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        agentTcpPortLabelPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        var agentTcpPortLabel = new JLabel(this.getMessage(AGENT_TCP_PORT_LABEL_MESSAGE_KEY));
        agentTcpPortLabelPanel.add(agentTcpPortLabel);
        contentPanel.add(agentTcpPortLabelPanel);
        var agentTcpPortTextFieldPanel = new JPanel();
        agentTcpPortTextFieldPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 50));
        agentTcpPortTextFieldPanel.setLayout(new BoxLayout(agentTcpPortTextFieldPanel, BoxLayout.Y_AXIS));
        agentTcpPortTextFieldPanel.setBorder(new EmptyBorder(5, 0, 10, 0));
        var agentTcpPortInput = new JTextField();
        agentTcpPortInput.setText(Integer.toString(this.agentConfiguration.getTcpPort()));
        agentTcpPortInput.setDisabledTextColor(new Color(200, 200, 200));
        agentTcpPortTextFieldPanel.add(agentTcpPortInput);
        contentPanel.add(agentTcpPortTextFieldPanel);
        var proxyAddressLabelPanel = new JPanel();
        proxyAddressLabelPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 30));
        proxyAddressLabelPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        proxyAddressLabelPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        var proxyAddressLabel = new JLabel(this.getMessage(PROXY_ADDRESS_LABEL_MESSAGE_KEY));
        proxyAddressLabelPanel.add(proxyAddressLabel);
        contentPanel.add(proxyAddressLabelPanel);
        var proxyAddressTextFieldPanel = new JPanel();
        proxyAddressTextFieldPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 50));
        proxyAddressTextFieldPanel.setLayout(new BoxLayout(proxyAddressTextFieldPanel, BoxLayout.Y_AXIS));
        proxyAddressTextFieldPanel.setBorder(new EmptyBorder(5, 0, 10, 0));
        var proxyAddressInput = new JTextField();
        proxyAddressInput.setText(this.agentConfiguration.getProxyHost());
        proxyAddressInput.setDisabledTextColor(new Color(200, 200, 200));
        proxyAddressTextFieldPanel.add(proxyAddressInput);
        contentPanel.add(proxyAddressTextFieldPanel);
        var proxyPortLabelPanel = new JPanel();
        proxyPortLabelPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 30));
        proxyPortLabelPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        proxyPortLabelPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
        var proxyPortLabel = new JLabel(this.getMessage(PROXY_PORT_LABEL_MESSAGE_KEY));
        proxyPortLabelPanel.add(proxyPortLabel);
        contentPanel.add(proxyPortLabelPanel);
        var proxyPortTextFieldPanel = new JPanel();
        proxyPortTextFieldPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 50));
        proxyPortTextFieldPanel.setLayout(new BoxLayout(proxyPortTextFieldPanel, BoxLayout.Y_AXIS));
        proxyPortTextFieldPanel.setBorder(new EmptyBorder(5, 0, 10, 0));
        var proxyPortInput = new JTextField();
        proxyPortInput.setText(Integer.toString(this.agentConfiguration.getProxyPort()));
        proxyPortInput.setDisabledTextColor(new Color(200, 200, 200));
        proxyPortTextFieldPanel.add(proxyPortInput);
        contentPanel.add(proxyPortTextFieldPanel);
        var buttonPanelLayout = new GridLayout(1, 2, 10, 0);
        var buttonPanel = new JPanel(buttonPanelLayout);
        buttonPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 50));
        var statusLabel = new JLabel(this.getMessage(STATUS_LABEL_DEFAULT_MESSAGE_KEY));
        var stopAllProxyBtn = new JButton(this.getMessage(BUTTON_STOP_PROXY_MESSAGE_KEY));
        var startProxyBtn = new JButton(
                this.getMessage(BUTTON_START_PROXY_MESSAGE_KEY));
        stopAllProxyBtn.setEnabled(false);
        stopAllProxyBtn.addActionListener(e -> {
                    try {
                        agent.stop();
                    } catch (Exception e1) {
                        logger.error("Fail to stop agent because of exception.", e1);
                    }
                    statusLabel.setText(getMessage(STATUS_LABEL_DEFAULT_MESSAGE_KEY));
                    tokenInput.setEditable(true);
                    tokenInput.setFocusable(true);
                    tokenInput.setEditable(true);
                    agentTcpPortInput.setEditable(true);
                    agentTcpPortInput.setFocusable(true);
                    agentTcpPortInput.setEditable(true);
                    proxyAddressInput.setEditable(true);
                    proxyAddressInput.setFocusable(true);
                    proxyAddressInput.setEditable(true);
                    proxyPortInput.setEditable(true);
                    proxyPortInput.setFocusable(true);
                    proxyPortInput.setEditable(true);
                    startProxyBtn.setEnabled(true);
                    stopAllProxyBtn.setEnabled(false);
                }
        );
        startProxyBtn.addActionListener(
                e -> {
                    if (!MainFrame.this.preVerifyToken(tokenInput)) {
                        statusLabel.setText(this.getMessage(STATUS_TOKEN_VALIDATION_FAIL_MESSAGE_KEY));
                        return;
                    }
                    this.agentConfiguration.setUserToken(tokenInput.getText());
                    if (StringUtils.isEmpty(this.agentConfiguration.getUserToken())) {
                        this.agentConfiguration.setUserToken(UUIDUtil.INSTANCE.generateUuid());
                    }
                    var tcpPort = -1;
                    try {
                        tcpPort = Integer.parseInt(agentTcpPortInput.getText());
                    } catch (Exception exception) {
                        statusLabel.setText(this.getMessage(STATUS_PORT_VALIDATION_FAIL_MESSAGE_KEY));
                        return;
                    }
                    this.agentConfiguration.setTcpPort(tcpPort);
                    var proxyPort = -1;
                    try {
                        proxyPort = Integer.parseInt(proxyPortInput.getText());
                    } catch (Exception exception) {
                        statusLabel.setText(this.getMessage(STATUS_PROXY_PORT_VALIDATION_FAIL_MESSAGE_KEY));
                        return;
                    }
                    this.agentConfiguration.setProxyPort(proxyPort);
                    this.agentConfiguration.setProxyHost(proxyAddressInput.getText());
                    try {
                        agent.start();
                    } catch (Exception e1) {
                        statusLabel.setText(this.getMessage(STATUS_AGENT_START_FAIL_MESSAGE_KEY));
                        logger.error("Fail to start http agent because of exception.", e1);
                        return;
                    }
                    statusLabel.setText(this.getMessage(STATUS_PROXY_IS_RUNNING_MESSAGE_KEY));
                    tokenInput.setEditable(false);
                    tokenInput.setFocusable(false);
                    tokenInput.setEditable(false);
                    agentTcpPortInput.setEditable(false);
                    agentTcpPortInput.setFocusable(false);
                    agentTcpPortInput.setEditable(false);
                    proxyAddressInput.setEditable(false);
                    proxyAddressInput.setFocusable(false);
                    proxyAddressInput.setEditable(false);
                    proxyPortInput.setEditable(false);
                    proxyPortInput.setFocusable(false);
                    proxyPortInput.setEditable(false);
                    stopAllProxyBtn.setEnabled(true);
                    startProxyBtn.setEnabled(false);
                    this.agentConfiguration.save();
                }
        );
        var adjustLoggerButton = new JButton(this.getMessage(BUTTON_ADJUST_LOGGER_MESSAGE_KEY));
        var adjustLoggerDialog =
                new JDialog(this, this.getMessage(DIALOG_ADJUST_LOGGER_TITLE_MESSAGE_KEY), true);
        this.initializeAdjustLoggerDialog(adjustLoggerDialog);
        adjustLoggerButton.addActionListener(e -> {
            adjustLoggerDialog.setVisible(true);
        });
        buttonPanel.add(startProxyBtn);
        buttonPanel.add(stopAllProxyBtn);
        buttonPanel.add(adjustLoggerButton);
        buttonPanel.setBorder(new EmptyBorder(0, 0, 10, 0));
        contentPanel.add(buttonPanel);
        var statusPanel = new JPanel(new CardLayout());
        statusPanel.setPreferredSize(new Dimension(PANEL_WIDTH, 20));
        statusPanel.add(statusLabel);
        contentPanel.add(statusPanel);
        return contentPanel;
    }

    private String getMessage(String statusTokenValidationFailMessageKey) {
        var locale = Locale.getDefault();
        if (this.agentConfiguration.getDefaultLocal() != null) {
            locale = this.agentConfiguration.getDefaultLocal();
        }
        return this.messageSource
                .getMessage(statusTokenValidationFailMessageKey, null, locale);
    }

    private void initializeAdjustLoggerDialog(JDialog adjustLoggerDialog) {
        adjustLoggerDialog.setResizable(false);
        var contentPanel = new JPanel();
        var contentPanelLayout = new BoxLayout(contentPanel, BoxLayout.Y_AXIS);
        contentPanel.setLayout(contentPanelLayout);
        adjustLoggerDialog.addWindowListener(new WindowAdapter() {
            public void windowClosed(WindowEvent e) {
                adjustLoggerDialog.setVisible(false);
            }
        });
        adjustLoggerDialog.setContentPane(contentPanel);
        var adjustLogLevelPanel = new JPanel();
        var adjustLogLevelPanelScrollPane = new JScrollPane(adjustLogLevelPanel);
        adjustLogLevelPanelScrollPane.setPreferredSize(new Dimension(PANEL_WIDTH, 500));
        adjustLogLevelPanelScrollPane.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        adjustLogLevelPanelScrollPane.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        adjustLogLevelPanel.setLayout(new BoxLayout(adjustLogLevelPanel, BoxLayout.Y_AXIS));
        adjustLogLevelPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        var loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerContext.getLoggers().stream().filter(it -> it.getName().startsWith("com.ppaass"))
                .sorted(Comparator.comparing(AbstractLogger::getName)).forEach(it -> {
            var loggerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            loggerPanel.setBorder(new EmptyBorder(0, 5, 5, 5));
            var selectLogLevelComboBox = new JComboBox<String>();
            for (int index = 0; index < Level.values().length; index++) {
                var item = Level.values()[index];
                selectLogLevelComboBox.addItem(item.name());
                if (it.getLevel() == item) {
                    selectLogLevelComboBox.setSelectedIndex(index);
                }
            }
            loggerPanel.add(selectLogLevelComboBox);
            loggerPanel.add(new JLabel(":: " + it.getName().substring(it.getName().lastIndexOf(".") + 1)));
            selectLogLevelComboBox.addActionListener(event -> {
                it.setLevel(Level.getLevel((String) selectLogLevelComboBox.getSelectedItem()));
            });
            adjustLogLevelPanel.add(loggerPanel);
        });
        contentPanel.add(adjustLogLevelPanelScrollPane);
        adjustLoggerDialog.pack();
    }

    public void start() {
        this.initialize();
        this.setVisible(true);
    }

    public void stop() {
        this.setVisible(false);
        this.agent.stop();
    }
}
