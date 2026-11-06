package com.example.shortener;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UrlPolicyTest {
    final UrlPolicy policy = new UrlPolicy();
    @ParameterizedTest @ValueSource(strings = {"javascript:alert(1)", "file:///etc/passwd", "ftp://example.com", "/relative", "https://user:pass@example.com", "https://example.com\r\nX-Evil: yes", "https://", "https://example.com:99999", "https://example.com:0"})
    void rejectsUnsafeOrMalformedTargets(String target) {
        assertThatThrownBy(() -> policy.validate(target)).isInstanceOf(ApiException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"https://example.com/a?b=1#c", "http://example.com:8080/path", "https://example.com/caf%C3%A9"})
    void preservesValidTargets(String target) { assertThat(policy.validate(target)).isEqualTo(target); }
}
