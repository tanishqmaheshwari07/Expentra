package com.example.expensetracker.util;

public final class MaskUtils {

    private MaskUtils() {
        // Utility class
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex < 0) {
            return email.charAt(0) + "***";
        }
        if (atIndex == 0) {
            return "***" + email.substring(atIndex);
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
