package com.globalcodelabs.socialmediaplanner.interfaces.rest.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserFacingErrorTest {

    @Test
    void shouldMapBlockedPrivateSourceWithoutExposingTechnicalDetails() {
        UserFacingError error = UserFacingError.source("Private network links are not allowed");

        assertThat(error.code()).isEqualTo("SOURCE_ADDRESS_NOT_ALLOWED");
        assertThat(error.message()).isEqualTo("Yerel veya özel ağ adresleri kaynak olarak kullanılamaz.");
    }
}
