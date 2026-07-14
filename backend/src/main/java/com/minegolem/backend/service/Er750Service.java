package com.minegolem.backend.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

@Service
public class Er750Service {

    private static final String READER_IP = "169.254.40.235";
    private static final int PORT = 2167;
    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 1_500;

    public void openDoor(int seconds) throws IOException {

        byte[] packet = buildOpenDoorPacket(seconds);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(READER_IP, PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            OutputStream out = socket.getOutputStream();
            out.write(packet);
            out.flush();

            try {
                byte[] response = socket.getInputStream().readNBytes(64);
                System.out.println("Response: " + bytesToHex(response));
            } catch (SocketTimeoutException ignored) {
                System.out.println("ER750 open-door command sent without response before timeout");
            }
        }
    }

    private byte[] buildOpenDoorPacket(int seconds) {

        byte[] cmd = {
                0x01,
                0x00,
                0x11,
                0x02,
                0x00,
                (byte) seconds
        };

        int crc = crc16(cmd, 1);

        byte[] packet = new byte[8];

        System.arraycopy(cmd, 0, packet, 0, cmd.length);

        packet[6] = (byte) (crc >> 8);
        packet[7] = (byte) crc;

        return packet;
    }

    private int crc16(byte[] data, int start) {

        int crc = 0xFFFF;

        for (int i = start; i < data.length; i++) {

            crc ^= (data[i] & 0xFF);

            for (int j = 0; j < 8; j++) {

                if ((crc & 1) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }

        return crc & 0xFFFF;
    }

    private String bytesToHex(byte[] bytes) {

        StringBuilder sb = new StringBuilder();

        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }

        return sb.toString();
    }
}
