package com.jitong.core.common;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;

public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Ids() {
    }

    public static String hex(String prefix, int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return prefix + HexFormat.of().withUpperCase().formatHex(buffer);
    }

    public static String token(String prefix, int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    public static String jobId() {
        return hex("J", 6);
    }

    public static String recordId() {
        return hex("R", 8);
    }

    public static String groupId() {
        return hex("G", 8);
    }

    public static String correctionId() {
        return hex("C", 8);
    }

    public static String employeeId() {
        return hex("E", 3);
    }

    public static String eventId() {
        return hex("GE", 8);
    }

    public static String routeKey() {
        return hex("R", 4);
    }

    public static String routeId(String routeKey) {
        return "RT-" + routeKey;
    }

    public static String qrToken() {
        return token("QJ-", 18);
    }

    public static String orderNo() {
        return "TB" + LocalDate.now().format(DAY) + (100 + RANDOM.nextInt(900));
    }

    public static String numericPin() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    public static String newIdempotencyKey() {
        return token("AUTO-", 18);
    }

    public static String groupRecordKey(String groupId, String employeeId) {
        return "GROUP-" + groupId + "-" + employeeId;
    }
}
