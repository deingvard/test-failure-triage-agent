#!/usr/bin/env python3
"""Generates the synthetic, labelled evaluation dataset (data/dataset.jsonl).

All cases are invented. No client, employer or production data is used.
The domain (payments, accounts, cards) is chosen only to make the failures look realistic.

About 1 in 5 cases is deliberately "hard": its surface signals point to a different
category (for example an AssertionError that is really bad test data), so a keyword-based
classifier cannot reach 100% and the dataset can show where an LLM adds value.

Run:  python3 data/generate_dataset.py
"""
import json
import pathlib

OUT = pathlib.Path(__file__).with_name("dataset.jsonl")

STABLE = "PPPPPPPP"
FLIPPY = ["PPFPPFPP", "PFPPPFPF", "FPPFPPPF", "PPPFPFPP", "PFPFPPPP"]


def frames(*lines):
    return "\n".join(f"\tat {line}" for line in lines)


def api_trace(test, err, msg):
    return f"{err}: {msg}\n" + frames(
        f"io.restassured.internal.ValidatableResponseImpl.statusCode(ValidatableResponseImpl.groovy:89)",
        f"com.example.payments.api.{test.replace('#', '.')}(" + test.split('#')[0] + ".java:57)",
        "org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:767)")


def ui_trace(test, err, msg):
    return f"{err}: {msg}\n" + frames(
        "com.codeborne.selenide.impl.WebElementSource.checkCondition(WebElementSource.java:122)",
        f"com.example.web.pages.{test.split('#')[0].replace('Test', 'Page')}.submit(" +
        test.split('#')[0].replace('Test', 'Page') + ".java:41)",
        f"com.example.web.{test.replace('#', '.')}(" + test.split('#')[0] + ".java:33)")


cases = []


def add(label, test, suite, err, msg, log, history, trace=None):
    if trace is None:
        trace = ui_trace(test, err, msg) if suite == "ui" else api_trace(test, err, msg)
    cases.append({
        "id": f"{label.lower()}-{sum(1 for c in cases if c['label'] == label) + 1:02d}",
        "test_name": test,
        "suite": suite,
        "error_type": err,
        "message": msg,
        "stack_trace": trace,
        "log": log,
        "history": history,
        "label": label,
    })


# ---------------------------------------------------------------- PRODUCT_BUG
L = "PRODUCT_BUG"
add(L, "TransfersApiTest#balanceIsDebitedAfterTransfer", "api", "org.opentest4j.AssertionFailedError",
    "expected: <150.00> but was: <145.00>",
    "INFO transfer tx-81723 created amount=50.00 fee=0.00\nINFO ledger posted debit=55.00", STABLE)
add(L, "RefundsApiTest#refundOfSettledPayment", "api", "java.lang.AssertionError",
    "1 expectation failed. Expected status code <200> but was <500>.",
    "ERROR [refund-service] java.lang.NullPointerException: Cannot invoke \"Payment.getSettlementDate()\" because \"payment\" is null\n\tat com.example.refund.RefundService.refund(RefundService.java:88)",
    STABLE)
add(L, "FxApiTest#quoteKeepsRequestedCurrency", "api", "org.opentest4j.AssertionFailedError",
    "expected: <\"EUR\"> but was: <\"USD\">", "INFO fx quote q-5521 created base=EUR target=PLN", STABLE)
add(L, "PaymentsContractTest#createPaymentMatchesSchema", "api", "io.restassured.module.jsv.JsonSchemaValidationException",
    "JSON schema validation failed: object has missing required properties ([\"transactionId\"])",
    "INFO POST /v2/payments 201 Created (38 ms)", STABLE)
add(L, "PaymentsApiTest#paymentIsIdempotentForSameKey", "api", "org.opentest4j.AssertionFailedError",
    "expected transactions count: <1> but was: <2>",
    "INFO Idempotency-Key=7f2c... request 1 -> tx-901\nINFO Idempotency-Key=7f2c... request 2 -> tx-902", STABLE)
add(L, "CheckoutUiTest#successfulCardPaymentShowsConfirmation", "ui", "com.codeborne.selenide.ex.ElementShouldNot",
    "Element should have text 'Payment completed' {.status-banner}, actual text: 'Payment failed: internal error'",
    "ERROR [checkout-api] 500 POST /api/checkout/confirm IllegalStateException: Order state CREATED cannot transition to PAID",
    STABLE)
add(L, "StatementsApiTest#statementIsSortedByDateDesc", "api", "org.opentest4j.AssertionFailedError",
    "expected list sorted by bookingDate descending, element [3] 2026-10-02 is after [2] 2026-09-28",
    "INFO GET /v1/accounts/acc-77/statement?page=1 200", STABLE)
add(L, "FeesApiTest#feeIsRoundedHalfUp", "api", "org.opentest4j.AssertionFailedError",
    "expected: <10.01> but was: <10.00>", "DEBUG fee raw=10.005 rounding=HALF_DOWN", STABLE)
# hard: timeout on UI, but the server is deadlocking after a release
add(L, "TransferUiTest#confirmTransferRedirectsToReceipt", "ui", "org.openqa.selenium.TimeoutException",
    "Expected condition failed: waiting for url to contain \"/receipt\" (tried for 15 second(s))",
    "ERROR [ledger-service] Deadlock detected while posting tx-4410, transaction rolled back (release 2.4.1)",
    "PPPPPPPF")
# hard: 503 that is really an application memory leak introduced by a release
add(L, "AccountsApiTest#listAccountsForCustomer", "api", "java.lang.AssertionError",
    "1 expectation failed. Expected status code <200> but was <503>.",
    "ERROR [accounts-service] java.lang.OutOfMemoryError: Java heap space after deploy 3.1.0; heap grows on every /accounts call",
    STABLE)

# ---------------------------------------------------------------- FLAKY_TEST
L = "FLAKY_TEST"
add(L, "DashboardUiTest#recentPaymentsWidgetLoads", "ui", "org.openqa.selenium.TimeoutException",
    "Expected condition failed: waiting for visibility of element located by By.cssSelector: .recent-payments li (tried for 10 second(s))",
    "DEBUG widget request /api/payments/recent took 9.8s", FLIPPY[0])
add(L, "CardsUiTest#freezeCardToggle", "ui", "org.openqa.selenium.StaleElementReferenceException",
    "stale element reference: stale element not found in the current frame",
    "DEBUG React re-render of CardList after websocket update", FLIPPY[1])
add(L, "NotificationsIT#paymentEventIsPublished", "integration", "org.awaitility.core.ConditionTimeoutException",
    "Condition with lambda expression in NotificationsIT was not fulfilled within 5 seconds.",
    "DEBUG kafka consumer lag=312 on topic payments.events", FLIPPY[2])
add(L, "BeneficiaryUiTest#addBeneficiary", "ui", "org.openqa.selenium.ElementClickInterceptedException",
    "element click intercepted: Element <button id=\"save\"> is not clickable at point (812, 640). Other element would receive the click: <div class=\"spinner-overlay\">",
    "", FLIPPY[3])
add(L, "LoginUiTest#loginWithOtp", "ui", "com.codeborne.selenide.ex.ElementShould",
    "Element should be visible {#otp-input}, Timeout: 4 s.",
    "DEBUG otp-service responded in 4.6s", FLIPPY[4])
add(L, "ReportsIT#exportFinishesInBackground", "integration", "org.awaitility.core.ConditionTimeoutException",
    "Condition was not fulfilled within 30 seconds: export job status expected DONE but was RUNNING",
    "DEBUG export job queue size=14", FLIPPY[1])
add(L, "SearchUiTest#filterByAmount", "ui", "org.openqa.selenium.StaleElementReferenceException",
    "stale element reference: element is not attached to the page document", "", FLIPPY[0])
# hard: looks like a product bug (assertion on a count) but async + flipping history
add(L, "AuditIT#everyTransferCreatesAuditEvents", "integration", "org.opentest4j.AssertionFailedError",
    "expected: <3> but was: <2>",
    "DEBUG audit consumer still processing offset 88120, assertion executed 120 ms after transfer",
    FLIPPY[2])
# hard: looks like a broken locator, but the element appears on other runs
add(L, "OnboardingUiTest#acceptTermsCheckbox", "ui", "org.openqa.selenium.NoSuchElementException",
    "no such element: Unable to locate element: {\"method\":\"css selector\",\"selector\":\"#terms\"}",
    "DEBUG page readyState=interactive at lookup time", FLIPPY[3])
# hard: looks like an environment problem, but only a slow call with a tight client timeout
add(L, "LimitsApiTest#dailyLimitIsReturned", "api", "java.net.SocketTimeoutException",
    "Read timed out", "DEBUG limits-service p99 latency 2.1s, client read timeout 2s", FLIPPY[4])

# ---------------------------------------------------------------- ENVIRONMENT
L = "ENVIRONMENT"
add(L, "PaymentsApiTest#createSepaPayment", "api", "java.net.ConnectException",
    "Connection refused: payments-api.staging.internal/10.12.4.7:8443", "", STABLE)
add(L, "AuthApiTest#tokenIsIssued", "api", "java.net.UnknownHostException",
    "auth.staging.internal: Name or service not known", "WARN DNS resolver 10.0.0.2 timeout", STABLE)
add(L, "CardsApiTest#issueVirtualCard", "api", "java.lang.AssertionError",
    "1 expectation failed. Expected status code <201> but was <503>.",
    "WARN gateway upstream connect error or disconnect/reset before headers. reset reason: connection failure", STABLE)
add(L, "PartnerApiTest#fetchRates", "api", "javax.net.ssl.SSLHandshakeException",
    "PKIX path validation failed: java.security.cert.CertPathValidatorException: validity check failed (certificate expired)",
    "", STABLE)
add(L, "CheckoutUiTest#openCheckoutPage", "ui", "org.openqa.selenium.SessionNotCreatedException",
    "Could not start a new session. Response code 500. Message: Could not start a new session. No nodes support the capabilities in the request",
    "WARN selenium-hub: queue size 23, no free chrome nodes", STABLE)
add(L, "LedgerIT#postingIsPersisted", "integration", "org.postgresql.util.PSQLException",
    "FATAL: the database system is in recovery mode", "", STABLE)
add(L, "ReportsIT#pdfStatementIsGenerated", "integration", "java.io.IOException",
    "No space left on device", "WARN agent ci-runner-07 /tmp usage 100%", STABLE)
add(L, "ProfileUiTest#changeAddress", "ui", "org.openqa.selenium.WebDriverException",
    "chrome not reachable", "WARN chrome process exited with code 137 (OOMKilled on runner)", "PPPPPPFP")
# hard: assertion on status code, but it is the proxy, not the app
add(L, "TransfersApiTest#internalTransfer", "api", "org.opentest4j.AssertionFailedError",
    "expected: <200> but was: <502>", "nginx: 502 Bad Gateway, upstream ledger-service pod restarting (node drain)", STABLE)
# hard: UI timeout, but the grid has no capacity
add(L, "StatementsUiTest#downloadStatement", "ui", "org.openqa.selenium.TimeoutException",
    "Expected condition failed: waiting for page to load (tried for 30 second(s))",
    "WARN selenium-hub: no available nodes, session waited 28s in queue", STABLE)

# ---------------------------------------------------------------- TEST_DATA
L = "TEST_DATA"
add(L, "RegistrationApiTest#registerNewCustomer", "api", "java.lang.AssertionError",
    "1 expectation failed. Expected status code <201> but was <409>. Body: {\"error\":\"User with email qa.user01@example.test already exists\"}",
    "INFO setup reused fixed email from fixtures/customers.json", STABLE)
add(L, "AccountsIT#openSecondAccount", "integration", "org.postgresql.util.PSQLException",
    "ERROR: duplicate key value violates unique constraint \"accounts_iban_key\"",
    "DEBUG @BeforeEach inserted hard-coded IBAN for test account", STABLE)
add(L, "TransfersApiTest#transferBetweenOwnAccounts", "api", "org.opentest4j.AssertionFailedError",
    "expected: <\"COMPLETED\"> but was: <\"REJECTED_INSUFFICIENT_FUNDS\">",
    "INFO test account acc-qa-02 balance=0.00 (shared by 4 suites, never topped up)", STABLE)
add(L, "LoginApiTest#loginWithPassword", "api", "java.lang.AssertionError",
    "1 expectation failed. Expected status code <200> but was <423>. Body: {\"error\":\"account locked after 3 failed attempts\"}",
    "INFO negative test WrongPasswordTest ran earlier with the same test user", STABLE)
add(L, "PaymentsApiTest#payFromFixture", "api", "java.io.FileNotFoundException",
    "src/test/resources/fixtures/payment_eur.json (No such file or directory)", "", STABLE)
add(L, "CardsApiTest#chargeTestCard", "api", "org.opentest4j.AssertionFailedError",
    "expected: <\"APPROVED\"> but was: <\"DECLINED_CARD_EXPIRED\">",
    "INFO using static test card with expiry 10/26 from test-data.yaml", STABLE)
add(L, "KycIT#verifiedCustomerCanTrade", "integration", "java.lang.IllegalStateException",
    "Precondition failed: no customer with KYC status VERIFIED found in test data", "", STABLE)
# hard: assertion on a business value, but the seed step silently did nothing
add(L, "BalanceApiTest#openingBalanceIsShown", "api", "org.opentest4j.AssertionFailedError",
    "expected: <100.00> but was: <0.00>", "WARN seed script skipped: accounts table already migrated, 0 rows inserted", STABLE)
# hard: 404 looks like a product bug, but the fixture was removed by a cleanup job
add(L, "CustomersApiTest#getCustomerProfile", "api", "java.lang.AssertionError",
    "1 expectation failed. Expected status code <200> but was <404>.",
    "INFO nightly cleanup job deleted customers created before 2026-11-01, including fixture cust-12345", STABLE)

# ---------------------------------------------------------------- BROKEN_LOCATOR
L = "BROKEN_LOCATOR"
add(L, "CheckoutUiTest#payByCard", "ui", "org.openqa.selenium.NoSuchElementException",
    "no such element: Unable to locate element: {\"method\":\"css selector\",\"selector\":\"#submit-payment\"}",
    "INFO frontend build 3.8.0 deployed to staging 40 min before the run", STABLE)
add(L, "TransferUiTest#fillTransferForm", "ui", "com.codeborne.selenide.ex.ElementNotFound",
    "Element not found {By.xpath: //button[text()='Pay now']} Expected: visible",
    "INFO button text changed to 'Confirm payment' in UI release 3.8.0", STABLE)
add(L, "LoginUiTest#loginButtonEnabled", "ui", "com.codeborne.selenide.ex.ElementNotFound",
    "Element not found {By.id: login-btn} Expected: enabled", "", STABLE)
add(L, "CardsUiTest#openCardDetails", "ui", "com.microsoft.playwright.TimeoutError",
    "Timeout 30000ms exceeded. waiting for locator(\"[data-test=card-details-link]\")",
    "INFO data-test attributes renamed to data-testid in design-system 5.0", STABLE)
add(L, "SettingsUiTest#toggleDarkMode", "ui", "org.openqa.selenium.NoSuchElementException",
    "no such element: Unable to locate element: {\"method\":\"xpath\",\"selector\":\"//div[@class='settings']/ul/li[4]/span\"}",
    "", STABLE)
add(L, "BeneficiaryUiTest#deleteBeneficiary", "ui", "com.codeborne.selenide.ex.ElementNotFound",
    "Element not found {.beneficiary-row .btn-delete} Expected: visible",
    "INFO beneficiary list migrated to new table component", STABLE)
add(L, "StatementsUiTest#filterByMonth", "ui", "org.openqa.selenium.NoSuchElementException",
    "no such element: Unable to locate element: {\"method\":\"css selector\",\"selector\":\"select[name='month']\"}",
    "INFO month dropdown replaced with date-range picker", STABLE)
# hard: generic timeout, but the cause is a renamed selector
add(L, "ProfileUiTest#saveProfile", "ui", "org.openqa.selenium.TimeoutException",
    "Expected condition failed: waiting for element to be clickable: By.id: save-profile (tried for 10 second(s))",
    "INFO profile form rebuilt in release 3.8.0, save button id is now profile-save", STABLE)
# hard: element "should be visible" assertion that reads like a product bug
add(L, "DashboardUiTest#balanceWidgetVisible", "ui", "com.codeborne.selenide.ex.ElementShould",
    "Element should be visible {.balance-widget}, Element: '<div class=\"balance-card\">1 250,00 EUR</div>'",
    "INFO balance widget CSS class renamed to balance-card, value rendered correctly", STABLE)
add(L, "NavigationUiTest#openSendMoneyFromMenu", "ui", "com.microsoft.playwright.TimeoutError",
    "Timeout 30000ms exceeded. waiting for locator(\"text=Send money\") to be visible, locator resolved to 0 elements",
    "INFO main menu item renamed to 'Transfer' in UI release 3.8.0", STABLE)

# extra TEST_DATA case, added after the BROKEN_LOCATOR block to keep ids stable
L = "TEST_DATA"
add(L, "MandatesApiTest#createDirectDebitMandate", "api", "java.lang.AssertionError",
    "1 expectation failed. Expected status code <201> but was <422>. Body: {\"error\":\"debtor account is closed\"}",
    "INFO test-data.yaml still points to debtor account acc-qa-09 closed in the previous sprint", STABLE)

if __name__ == "__main__":
    with OUT.open("w", encoding="utf-8") as f:
        for c in cases:
            f.write(json.dumps(c, ensure_ascii=False) + "\n")
    by_label = {}
    for c in cases:
        by_label[c["label"]] = by_label.get(c["label"], 0) + 1
    print(f"wrote {len(cases)} cases to {OUT}: {by_label}")
