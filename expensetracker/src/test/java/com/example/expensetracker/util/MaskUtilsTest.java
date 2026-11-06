package com.example.expensetracker.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskUtilsTest {

    @Test
    @DisplayName("Should mask standard email correctly")
    void maskStandardEmail() {
        assertThat(MaskUtils.maskEmail("leaktest@example.com")).isEqualTo("l***@example.com");
        assertThat(MaskUtils.maskEmail("alex@domain.org")).isEqualTo("a***@domain.org");
    }

    @Test
    @DisplayName("Should handle null and blank input")
    void maskNullAndBlank() {
        assertThat(MaskUtils.maskEmail(null)).isEqualTo("***");
        assertThat(MaskUtils.maskEmail("")).isEqualTo("***");
        assertThat(MaskUtils.maskEmail("   ")).isEqualTo("***");
    }

    @Test
    @DisplayName("Should handle email without local part or without domain")
    void maskEdgeCases() {
        assertThat(MaskUtils.maskEmail("@example.com")).isEqualTo("***@example.com");
        assertThat(MaskUtils.maskEmail("plainstring")).isEqualTo("p***");
    }
}
