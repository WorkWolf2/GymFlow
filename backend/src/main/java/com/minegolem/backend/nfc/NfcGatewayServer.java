package com.minegolem.backend.nfc;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@ConditionalOnProperty(name = "nfc.tcp.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class NfcGatewayServer {

    @Value("${nfc.tcp.port:2169}")
    private int port;

    @Value("${nfc.tcp.host:0.0.0.0}")
    private String host;

    @Value("${nfc.tcp.mode:client}")
    private String mode;

    @Value("${nfc.tcp.remote-host:172.30.80.1}")
    private String remoteHost;

    @Value("${nfc.tcp.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${nfc.tcp.reconnect-delay-ms:3000}")
    private long reconnectDelayMs;

    private final NfcConnectionHandler connectionHandler;

    private ServerSocket serverSocket;
    private volatile Socket eventSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void start() {
        running.set(true);
        if ("server".equalsIgnoreCase(mode)) {
            executor.submit(this::listen);
            log.info("NFC TCP listener starting on {}:{}", host, port);
        } else {
            executor.submit(this::connectToEventServer);
            log.info("NFC event client starting for {}:{}", remoteHost, port);
        }
    }

    private void listen() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
            log.info("NFC Gateway TCP server listening on {}:{}", host, port);

            while (running.get() && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    String clientIp = client.getInetAddress().getHostAddress();
                    log.debug("NFC controller connected from {}", clientIp);
                    executor.submit(() -> connectionHandler.handle(client, clientIp));
                } catch (IOException e) {
                    if (running.get()) {
                        log.error("Error accepting NFC connection", e);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to start NFC TCP server on port {}", port, e);
        }
    }

    private void connectToEventServer() {
        while (running.get()) {
            try {
                Socket socket = new Socket();
                eventSocket = socket;
                socket.setKeepAlive(true);
                socket.connect(new InetSocketAddress(remoteHost, port), connectTimeoutMs);
                log.info("Connected to NFC event server at {}:{}", remoteHost, port);
                connectionHandler.handle(socket, remoteHost);

                if (running.get()) {
                    log.warn("NFC event server {}:{} disconnected; reconnecting", remoteHost, port);
                }
            } catch (IOException e) {
                if (running.get()) {
                    log.warn("Cannot connect to NFC event server {}:{}: {}", remoteHost, port, e.getMessage());
                }
            } finally {
                eventSocket = null;
            }

            if (running.get()) {
                try {
                    Thread.sleep(reconnectDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdown();
        if (eventSocket != null) {
            try {
                eventSocket.close();
            } catch (IOException e) {
                log.warn("Error closing NFC event connection", e);
            }
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("Error closing NFC server socket", e);
            }
        }
    }
}
