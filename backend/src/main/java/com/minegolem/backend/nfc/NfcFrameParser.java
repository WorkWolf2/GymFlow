package com.minegolem.backend.nfc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Component
@Slf4j
public class NfcFrameParser {

    private static final String TEXT_PREFIX_TAG = "TAG:";
    private static final String TEXT_PREFIX_DEV = "DEV:";
    private static final byte[] BINARY_MAGIC = {0x47, 0x59, 0x34, 0x41}; // GY4A
    private static final Pattern ER_DEVICE_PATTERN = Pattern.compile("[A-Z]{2}\\d{3}-[A-Z0-9]{4}");

    public record NfcFrame(String tagUid, String deviceId) {}

    public Optional<NfcFrame> parseFrame(byte[] raw, int length) {
        if (length <= 0) return Optional.empty();

        try {
            // Try text protocol first
            String text = new String(raw, 0, length, StandardCharsets.UTF_8).trim();
            Optional<NfcFrame> textResult = parseText(text);
            if (textResult.isPresent()) return textResult;

            // Try binary protocol
            if (length >= 6 && isBinaryMagic(raw)) {
                return parseBinary(raw, length);
            }

            Optional<NfcFrame> erReaderResult = parseErReaderFrame(raw, length, text);
            if (erReaderResult.isPresent()) return erReaderResult;

            log.warn("Unrecognized NFC frame format, raw hex: {}", HexFormat.of().formatHex(raw, 0, Math.min(length, 32)));
            return Optional.empty();

        } catch (Exception e) {
            log.error("Frame parsing error", e);
            return Optional.empty();
        }
    }

    private Optional<NfcFrame> parseText(String text) {
        // Format: TAG:04AB1234|DEV:door-1
        if (!text.startsWith(TEXT_PREFIX_TAG)) return Optional.empty();

        String body = text.substring(TEXT_PREFIX_TAG.length());
        String[] parts = body.split("\\|");

        String tagUid = parts[0].trim().toUpperCase();
        String deviceId = "unknown";

        if (parts.length >= 2 && parts[1].startsWith(TEXT_PREFIX_DEV)) {
            deviceId = parts[1].substring(TEXT_PREFIX_DEV.length()).trim();
        }

        if (tagUid.isEmpty()) return Optional.empty();
        return Optional.of(new NfcFrame(tagUid, deviceId));
    }

    private boolean isBinaryMagic(byte[] raw) {
        for (int i = 0; i < BINARY_MAGIC.length; i++) {
            if (raw[i] != BINARY_MAGIC[i]) return false;
        }
        return true;
    }

    private Optional<NfcFrame> parseBinary(byte[] raw, int length) {
        // magic(4) + uid_len(1) + uid(n) + dev_len(1) + dev(m)
        int idx = BINARY_MAGIC.length;
        if (idx >= length) return Optional.empty();

        int uidLen = raw[idx++] & 0xFF;
        if (idx + uidLen > length) return Optional.empty();

        String tagUid = HexFormat.of().formatHex(raw, idx, idx + uidLen).toUpperCase();
        idx += uidLen;

        String deviceId = "unknown";
        if (idx < length) {
            int devLen = raw[idx++] & 0xFF;
            if (idx + devLen <= length) {
                deviceId = new String(raw, idx, devLen, StandardCharsets.UTF_8);
            }
        }

        return Optional.of(new NfcFrame(tagUid, deviceId));
    }

    private Optional<NfcFrame> parseErReaderFrame(byte[] raw, int length, String text) {
        Matcher matcher = ER_DEVICE_PATTERN.matcher(text);
        if (!matcher.find()) return Optional.empty();

        String deviceId = matcher.group();
        int uidLen = raw.length > 2 ? raw[2] & 0xFF : 0;
        int uidStart = 6;
        if (uidLen <= 0 || uidLen > 10 || uidStart + uidLen > length) {
            uidLen = 4;
        }
        if (uidStart + uidLen > length) return Optional.empty();

        String tagUid = HexFormat.of().formatHex(raw, uidStart, uidStart + uidLen).toUpperCase();
        if (tagUid.isBlank() || tagUid.matches("0+")) return Optional.empty();
        return Optional.of(new NfcFrame(tagUid, deviceId));
    }
}
