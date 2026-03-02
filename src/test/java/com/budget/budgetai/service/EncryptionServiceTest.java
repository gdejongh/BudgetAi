package com.budget.budgetai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        // Use a base64-encoded 32-byte key for testing
        encryptionService = new EncryptionService("dGVzdC1lbmNyeXB0aW9uLWtleS0xMjM0NTY3ODk=");
    }

    @Test
    void encrypt_decrypt_roundTrip_returnsOriginal() {
        String plaintext = "access-sandbox-12345678-abcd-efgh-ijkl-mnopqrstuvwx";
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_producesBase64Output() {
        String plaintext = "test-token";
        String encrypted = encryptionService.encrypt(plaintext);

        assertNotNull(encrypted);
        assertFalse(encrypted.isEmpty());
        // Should be valid base64
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(encrypted));
    }

    @Test
    void encrypt_sameInput_producesDifferentOutputs() {
        String plaintext = "test-token";
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        // Due to random IV, same plaintext should produce different ciphertext
        assertNotEquals(encrypted1, encrypted2);
    }

    @Test
    void decrypt_invalidCiphertext_throwsException() {
        assertThrows(RuntimeException.class, () -> encryptionService.decrypt("not-valid-ciphertext"));
    }

    @Test
    void encrypt_emptyString_roundTrips() {
        String plaintext = "";
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_longString_roundTrips() {
        String plaintext = "a".repeat(10000);
        String encrypted = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }

    @Test
    void construct_withRawStringKey_works() {
        EncryptionService service = new EncryptionService("my-raw-string-key-that-is-32-byt");
        String plaintext = "test";
        String encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);

        assertEquals(plaintext, decrypted);
    }
}
