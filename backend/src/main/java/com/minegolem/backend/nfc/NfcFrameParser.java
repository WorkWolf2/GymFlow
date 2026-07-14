package com.minegolem.backend.nfc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
    private static final Pattern KEYED_TEXT_TAG_PATTERN = Pattern.compile(
        "(?:TAG|UID|CARD|BADGE|NFC|RFID)\\s*[:=]\\s*([A-Fa-f0-9][A-Fa-f0-9:\\-\\s]{3,})"
    );
    private static final Pattern HEX_TEXT_PATTERN = Pattern.compile("^[A-Fa-f0-9:\\-\\s]+$");

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

            Optional<NfcFrame> fallbackResult = parseRawCardFrame(raw, length, text);
            if (fallbackResult.isPresent()) return fallbackResult;

            log.warn("Unrecognized NFC frame format, raw hex: {}", HexFormat.of().formatHex(raw, 0, Math.min(length, 32)));
            return Optional.empty();

        } catch (Exception e) {
            log.error("Frame parsing error", e);
            return Optional.empty();
        }
    }

    private Optional<NfcFrame> parseText(String text) {
        // Format: TAG:04AB1234|DEV:door-1
        if (!text.startsWith(TEXT_PREFIX_TAG)) {
            return parseKeyedText(text);
        }

        String body = text.substring(TEXT_PREFIX_TAG.length());
        String[] parts = body.split("\\|");

        String tagUid = normalizeTag(parts[0]);
        String deviceId = "unknown";

        if (parts.length >= 2 && parts[1].startsWith(TEXT_PREFIX_DEV)) {
            deviceId = parts[1].substring(TEXT_PREFIX_DEV.length()).trim();
        }

        if (tagUid.isEmpty()) return Optional.empty();
        return Optional.of(new NfcFrame(tagUid, deviceId));
    }

    private Optional<NfcFrame> parseKeyedText(String text) {
        Matcher matcher = KEYED_TEXT_TAG_PATTERN.matcher(text);
        if (matcher.find()) {
            String tagUid = normalizeTag(matcher.group(1));
            if (!tagUid.isEmpty()) {
                return Optional.of(new NfcFrame(tagUid, extractDeviceId(text)));
            }
        }

        if (text.length() >= 4 && text.length() <= 64 && HEX_TEXT_PATTERN.matcher(text).matches()) {
            String tagUid = normalizeTag(text);
            if (!tagUid.isEmpty() && tagUid.length() % 2 == 0) {
                return Optional.of(new NfcFrame(tagUid, "unknown"));
            }
        }

        return Optional.empty();
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
        int uidLen = length > 2 ? raw[2] & 0xFF : 0;
        int uidStart = 6;
        if (uidLen <= 0 || uidLen > 10 || uidStart + uidLen > length) {
            uidLen = 4;
        }
        if (uidStart + uidLen > length) return Optional.empty();

        String tagUid = HexFormat.of().formatHex(raw, uidStart, uidStart + uidLen).toUpperCase();
        if (tagUid.isBlank() || tagUid.matches("0+")) return Optional.empty();
        return Optional.of(new NfcFrame(tagUid, deviceId));
    }

    private Optional<NfcFrame> parseRawCardFrame(byte[] raw, int length, String text) {
        byte[] frame = trimTrailingZeros(raw, length);
        if (frame.length < 4) return Optional.empty();

        String deviceId = extractDeviceId(text);

        Optional<String> lengthPrefixedUid = extractLengthPrefixedUid(frame);
        if (lengthPrefixedUid.isPresent()) {
            return Optional.of(new NfcFrame(lengthPrefixedUid.get(), deviceId));
        }

        Optional<String> wiegandCode = extractWiegandCode(frame);
        if (wiegandCode.isPresent()) {
            return Optional.of(new NfcFrame(wiegandCode.get(), deviceId));
        }

        String rawHex = HexFormat.of().formatHex(frame).toUpperCase();
        if (rawHex.length() >= 8 && rawHex.length() <= 32 && !rawHex.matches("0+")) {
            log.info("Using raw NFC frame as tag UID: {}", rawHex);
            return Optional.of(new NfcFrame(rawHex, deviceId));
        }

        return Optional.empty();
    }

    private Optional<String> extractLengthPrefixedUid(byte[] frame) {
        List<Integer> starts = new ArrayList<>();
        starts.add(6);
        starts.add(5);
        starts.add(4);

        for (int start : starts) {
            int lenIndex = start - 4;
            if (lenIndex < 0 || lenIndex >= frame.length) continue;

            int uidLen = frame[lenIndex] & 0xFF;
            if (uidLen < 4 || uidLen > 10 || start + uidLen > frame.length) continue;

            String tagUid = HexFormat.of().formatHex(frame, start, start + uidLen).toUpperCase();
            if (!tagUid.matches("0+")) {
                return Optional.of(tagUid);
            }
        }

        return Optional.empty();
    }

    private Optional<String> extractWiegandCode(byte[] frame) {
        if (frame.length < 4) return Optional.empty();

        int start = frame.length >= 8 ? frame.length - 6 : frame.length - 4;
        if (start < 0 || start + 4 > frame.length) return Optional.empty();

        String tagUid = HexFormat.of().formatHex(frame, start, start + 4).toUpperCase();
        if (tagUid.isBlank() || tagUid.matches("0+")) return Optional.empty();

        return Optional.of(tagUid);
    }

    private String extractDeviceId(String text) {
        Matcher matcher = ER_DEVICE_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : "unknown";
    }

    private String normalizeTag(String value) {
        return value == null ? "" : value.replaceAll("[^A-Fa-f0-9]", "").toUpperCase();
    }

    private byte[] trimTrailingZeros(byte[] raw, int length) {
        int end = Math.min(length, raw.length);
        while (end > 0 && raw[end - 1] == 0) {
            end--;
        }

        byte[] trimmed = new byte[end];
        System.arraycopy(raw, 0, trimmed, 0, end);
        return trimmed;
    }
}
