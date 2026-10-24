package com.dataplatform.ai.confirmation;

import lombok.Data;

@Data
public class ConfirmationContext {

    private static final ThreadLocal<String> confirmationTokenHolder = new ThreadLocal<>();

    public static void setToken(String token) {
        confirmationTokenHolder.set(token);
    }

    public static String getToken() {
        return confirmationTokenHolder.get();
    }

    public static void clear() {
        confirmationTokenHolder.remove();
    }

    public static boolean hasToken() {
        return confirmationTokenHolder.get() != null;
    }
}
