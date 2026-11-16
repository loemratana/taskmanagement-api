package com.ratana.task_management_api.infrastructure.firebase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class FirebaseConfigTest {

    private static final String JSON = "{\"type\":\"service_account\",\"project_id\":\"demo\"}";

    @Test
    @DisplayName("FIREBASE_CREDENTIALS may be the raw JSON or its base64, with line breaks")
    void serviceAccountJson_acceptsJsonOrBase64() {
        assertThat(new String(FirebaseConfig.serviceAccountJson("  " + JSON + "\n"), StandardCharsets.UTF_8))
                .isEqualTo(JSON);

        String base64 = Base64.getMimeEncoder(16, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(JSON.getBytes(StandardCharsets.UTF_8));
        assertThat(new String(FirebaseConfig.serviceAccountJson(base64), StandardCharsets.UTF_8)).isEqualTo(JSON);
    }
}
