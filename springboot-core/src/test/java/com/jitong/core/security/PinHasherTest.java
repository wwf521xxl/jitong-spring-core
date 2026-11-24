package com.jitong.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PinHasherTest {

    private final PinHasher hasher = new PinHasher();

    @Test
    void hashAndVerify() {
        String encoded = hasher.hash("123456");

        assertThat(encoded).startsWith("pbkdf2_sha256$160000$");
        assertThat(hasher.matches("123456", encoded)).isTrue();
        assertThat(hasher.matches("654321", encoded)).isFalse();
    }

    @Test
    void verifiesLegacyGeneratedHash() {
        // 由历史 PBKDF2 实现生成，保证历史数据升级后账号可直接登录。
        String legacyHash = "pbkdf2_sha256$160000$MDEyMzQ1Njc4OWFiY2RlZg==$"
                + "L2U9DnbaHFEeIvqzhKGul54hsOW_1EIvm0sUf_nMOlc=";

        assertThat(hasher.matches("123456", legacyHash)).isTrue();
        assertThat(hasher.matches("000000", legacyHash)).isFalse();
    }

    @Test
    void rejectsMalformedHashes() {
        assertThat(hasher.matches("123456", "not-a-hash")).isFalse();
        assertThat(hasher.matches("123456", null)).isFalse();
    }
}
