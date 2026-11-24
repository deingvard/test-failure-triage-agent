# Evaluation report: `rules` classifier

| Metric | Value |
|---|---|
| Cases | 50 |
| Accuracy | 88.0% (44/50) |
| Macro F1 | 0.882 |
| Escalated to a human | 14 |
| Product bugs silenced (RERUN/QUARANTINE) | 0 |

## Per category

| Category | Precision | Recall | F1 |
|---|---|---|---|
| PRODUCT_BUG | 0.67 | 1.00 | 0.80 |
| FLAKY_TEST | 0.91 | 1.00 | 0.95 |
| ENVIRONMENT | 1.00 | 0.90 | 0.95 |
| TEST_DATA | 1.00 | 0.70 | 0.82 |
| BROKEN_LOCATOR | 1.00 | 0.80 | 0.89 |

## Confusion matrix (rows = actual, columns = predicted)

| | PRODUCT_BUG | FLAKY_TEST | ENVIRONMENT | TEST_DATA | BROKEN_LOCATOR | UNKNOWN |
|---|---|---|---|---|---|---|
| PRODUCT_BUG | 10 | 0 | 0 | 0 | 0 | 0 |
| FLAKY_TEST | 0 | 10 | 0 | 0 | 0 | 0 |
| ENVIRONMENT | 0 | 1 | 9 | 0 | 0 | 0 |
| TEST_DATA | 3 | 0 | 0 | 7 | 0 | 0 |
| BROKEN_LOCATOR | 2 | 0 | 0 | 0 | 8 | 0 |

## Misclassified cases (6)

| Case | Actual | Predicted | Confidence | Final action |
|---|---|---|---|---|
| environment-08 | ENVIRONMENT | FLAKY_TEST | 0.50 | ESCALATE |
| test_data-08 | TEST_DATA | PRODUCT_BUG | 0.67 | FILE_BUG |
| test_data-09 | TEST_DATA | PRODUCT_BUG | 0.67 | FILE_BUG |
| broken_locator-08 | BROKEN_LOCATOR | PRODUCT_BUG | 0.33 | ESCALATE |
| broken_locator-09 | BROKEN_LOCATOR | PRODUCT_BUG | 0.33 | ESCALATE |
| test_data-10 | TEST_DATA | PRODUCT_BUG | 0.40 | ESCALATE |
