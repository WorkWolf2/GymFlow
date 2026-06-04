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

    private final NfcConnectionHandler connectionHandler;

    private ServerSocket serverSocket;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    public void start() {
        executor.submit(this::listen);
        log.info("NFC Gateway TCP server starting on port {}", port);
    }

    private void listen() {
        try {
            serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
            running.set(true);
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

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdown();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("Error closing NFC server socket", e);
            }
        }
    }
}
