package com.dataplatform.auth.util;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordEncoderTest {

    @Test
    public void generateBCryptHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String password = "280976";
        String encoded = encoder.encode(password);

        System.out.println("============================================");
        System.out.println("Password: " + password);
        System.out.println("BCrypt Hash: " + encoded);
        System.out.println("Verification: " + encoder.matches(password, encoded));
        System.out.println("============================================");
    }
}
