package io.github.deingvard.triage.guard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            email         | user qa.user01@example.test exists                      | qa.user01@example.test     | [EMAIL]
            jwt           | token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.c2ln ok        | eyJhbGciOiJIUzI1NiJ9       | [JWT]
            bearer        | Authorization: Bearer abcdef1234567890XYZ               | abcdef1234567890XYZ        | Bearer [TOKEN]
            card spaces   | card 4111 1111 1111 1111 entered                        | 4111 1111 1111 1111        | [CARD]
            card plain    | pan=5555555555554444                                    | 5555555555554444           | [CARD]
            iban          | debtor LT12 1000 0111 0100 1000 closed                  | LT12 1000 0111 0100 1000   | [IBAN]
            ipv4          | Connection refused: host/10.12.4.7:8443                 | 10.12.4.7                  | [IP]
            password      | login with password=Secr3t! failed                      | Secr3t!                    | password=[SECRET]
            api key       | header api_key: sk-live-0123456789abcdefXYZ             | sk-live-0123456789abcdef   | [SECRET]
            """)
    @DisplayName("masks secrets and personal data")
    void masksSensitiveValues(String kind, String input, String secret, String expectedMarker) {
        Redactor.Result result = Redactor.redact(input);

        assertThat(result.text()).doesNotContain(secret).contains(expectedMarker);
        assertThat(result.total()).isPositive();
    }

    @Test
    @DisplayName("does not mask numbers that fail the Luhn check, such as order ids")
    void keepsNonCardNumbers() {
        String input = "order 1234567890123 failed";

        assertThat(Redactor.redact(input).text()).isEqualTo(input);
    }

    @Test
    @DisplayName("leaves ordinary failure text untouched")
    void keepsPlainText() {
        String input = "expected: <150.00> but was: <145.00> at TransfersApiTest.java:57";

        Redactor.Result result = Redactor.redact(input);

        assertThat(result.text()).isEqualTo(input);
        assertThat(result.total()).isZero();
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(Redactor.redact((String) null).text()).isEmpty();
        assertThat(Redactor.redact("").text()).isEmpty();
    }

    @Test
    void luhn() {
        assertThat(Redactor.luhnValid("4111111111111111")).isTrue();
        assertThat(Redactor.luhnValid("4111111111111112")).isFalse();
    }
}
