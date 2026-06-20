package com.estudos.contabancaria.adapter.in.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @Test
    void acceptsValidClientHeader() {
        assertThat(RequestIdFilter.resolve("req-123_ABC.4")).isEqualTo("req-123_ABC.4");
    }

    @Test
    void generatesUuidWhenHeaderIsNull() {
        assertThat(RequestIdFilter.resolve(null)).matches(UUID_REGEX);
    }

    @Test
    void rejectsHeaderWithCrlfToPreventLogForging() {
        String resolved = RequestIdFilter.resolve("evil\r\nINJECTED line");
        assertThat(resolved).doesNotContain("\n").doesNotContain("\r");
        assertThat(resolved).matches(UUID_REGEX); // caiu no fallback gerado
    }

    @Test
    void rejectsTooLongHeader() {
        String tooLong = "a".repeat(65);
        assertThat(RequestIdFilter.resolve(tooLong)).isNotEqualTo(tooLong);
    }
}
