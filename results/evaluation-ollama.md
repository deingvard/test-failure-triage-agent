# Evaluation report: `ollama` classifier

| Metric | Value |
|---|---|
| Cases | 50 |
| Accuracy | 70.0% (35/50) |
| Macro F1 | 0.674 |
| Escalated to a human | 1 |
| Product bugs silenced (RERUN/QUARANTINE) | 0 |

## Per category

| Category | Precision | Recall | F1 |
|---|---|---|---|
| PRODUCT_BUG | 0.50 | 0.90 | 0.64 |
| FLAKY_TEST | 0.80 | 0.40 | 0.53 |
| ENVIRONMENT | 0.90 | 0.90 | 0.90 |
| TEST_DATA | 1.00 | 0.30 | 0.46 |
| BROKEN_LOCATOR | 0.71 | 1.00 | 0.83 |

## Confusion matrix (rows = actual, columns = predicted)

| | PRODUCT_BUG | FLAKY_TEST | ENVIRONMENT | TEST_DATA | BROKEN_LOCATOR | UNKNOWN |
|---|---|---|---|---|---|---|
| PRODUCT_BUG | 9 | 1 | 0 | 0 | 0 | 0 |
| FLAKY_TEST | 1 | 4 | 1 | 0 | 4 | 0 |
| ENVIRONMENT | 1 | 0 | 9 | 0 | 0 | 0 |
| TEST_DATA | 7 | 0 | 0 | 3 | 0 | 0 |
| BROKEN_LOCATOR | 0 | 0 | 0 | 0 | 10 | 0 |

## Misclassified cases (15)

| Case | Actual | Predicted | Confidence | Final action |
|---|---|---|---|---|
| product_bug-09 | PRODUCT_BUG | FLAKY_TEST | 0.80 | ESCALATE |
| flaky_test-04 | FLAKY_TEST | BROKEN_LOCATOR | 1.00 | UPDATE_LOCATOR |
| flaky_test-05 | FLAKY_TEST | BROKEN_LOCATOR | 0.90 | UPDATE_LOCATOR |
| flaky_test-07 | FLAKY_TEST | BROKEN_LOCATOR | 0.90 | UPDATE_LOCATOR |
| flaky_test-08 | FLAKY_TEST | PRODUCT_BUG | 1.00 | FILE_BUG |
| flaky_test-09 | FLAKY_TEST | BROKEN_LOCATOR | 1.00 | UPDATE_LOCATOR |
| flaky_test-10 | FLAKY_TEST | ENVIRONMENT | 0.90 | NOTIFY_INFRA |
| environment-03 | ENVIRONMENT | PRODUCT_BUG | 1.00 | FILE_BUG |
| test_data-01 | TEST_DATA | PRODUCT_BUG | 1.00 | FILE_BUG |
| test_data-03 | TEST_DATA | PRODUCT_BUG | 1.00 | FILE_BUG |
| test_data-04 | TEST_DATA | PRODUCT_BUG | 1.00 | FILE_BUG |
| test_data-06 | TEST_DATA | PRODUCT_BUG | 1.00 | FILE_BUG |
| test_data-08 | TEST_DATA | PRODUCT_BUG | 1.00 | FILE_BUG |
| test_data-09 | TEST_DATA | PRODUCT_BUG | 1.00 | FILE_BUG |
| test_data-10 | TEST_DATA | PRODUCT_BUG | 1.00 | FILE_BUG |
