package com.example.tokenservice;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 整合測試共用設定：金鑰寫到暫存目錄，避免汙染專案下的 {@code ./keys}。
 * 所有子類別共用同一個 Spring context 與同一組金鑰。
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    protected static final String ISSUER = "http://localhost:8080";

    private static final Path KEY_DIR;

    static {
        try {
            KEY_DIR = Files.createTempDirectory("token-service-keys");
        }
        catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void keyLocation(DynamicPropertyRegistry registry) {
        registry.add("app.keys.private-key-path", () -> KEY_DIR.resolve("private.pem").toString());
        registry.add("app.keys.public-key-path", () -> KEY_DIR.resolve("public.pem").toString());
        registry.add("app.jwt.issuer", () -> ISSUER);
    }
}
