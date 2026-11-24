package io.github.deingvard.triage.io;

import io.github.deingvard.triage.model.FailureCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JUnitXmlReaderTest {

    private static final Path SAMPLE = Path.of("data/sample-report");

    @Test
    @DisplayName("extracts failures and errors, skips passed and skipped tests")
    void readsSampleReport() {
        List<FailureCase> failures = JUnitXmlReader.read(SAMPLE, SAMPLE.resolve("history.json"));

        assertThat(failures).extracting(FailureCase::testName).containsExactlyInAnyOrder(
                "PaymentsApiTest#paymentIsIdempotentForSameKey",
                "PaymentsApiTest#registerNewCustomer",
                "PaymentsApiTest#fetchPartnerRates",
                "CheckoutUiTest#payByCard",
                "CheckoutUiTest#recentPaymentsWidgetLoads");
    }

    @Test
    @DisplayName("maps type, message, logs and history of a failure")
    void mapsFields() {
        FailureCase flaky = JUnitXmlReader.read(SAMPLE, SAMPLE.resolve("history.json")).stream()
                .filter(f -> f.testName().endsWith("recentPaymentsWidgetLoads"))
                .findFirst().orElseThrow();

        assertThat(flaky.errorType()).isEqualTo("org.openqa.selenium.TimeoutException");
        assertThat(flaky.suite()).isEqualTo("ui");
        assertThat(flaky.log()).contains("took 9.8s");
        assertThat(flaky.history()).isEqualTo("PPFPPFPP");
        assertThat(flaky.label()).isNull();
    }

    @Test
    @DisplayName("rejects reports with a DOCTYPE (XXE protection)")
    void rejectsXxe(@TempDir Path dir) throws IOException {
        Path evil = dir.resolve("TEST-evil.xml");
        Files.writeString(evil, """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <testsuite><testcase name="a" classname="b"><failure message="&xxe;"/></testcase></testsuite>
                """);

        assertThatThrownBy(() -> JUnitXmlReader.read(evil, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot parse");
    }
}
