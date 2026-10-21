package com.dataplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class PasswordTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void generatePassword() {
        String rawPassword = "admin123";
        String encodedPassword = passwordEncoder.encode(rawPassword);
        System.out.println("Raw Password: " + rawPassword);
        System.out.println("Encoded Password: " + encodedPassword);
        System.out.println("Matches: " + passwordEncoder.matches(rawPassword, encodedPassword));
    }

    @Test
    public void testExistingPassword() {
        String rawPassword = "admin123";
        String existingHash = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lBOsl7iKBqKvLW6qe";
        System.out.println("Testing password: " + rawPassword);
        System.out.println("Against hash: " + existingHash);
        System.out.println("Matches: " + passwordEncoder.matches(rawPassword, existingHash));
    }
}
