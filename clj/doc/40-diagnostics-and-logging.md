# Diagnostics and Logging

## Diagnostics for mismatched balances

A bother when importing transactions is finding that the asserted balance is different from the sum of the postings.

To facilitate resolving such problems, when a balance mismatch is detected, all posts to that account since the
most recent balance assertion are printed along with the error message.

For example:
```
aya> limabean --beanfile examples/beancount/balance.beancount
Error: invalid balance
    ╭─[ examples/beancount/balance.beancount:27:1 ]
 27 │ 2020-02-28 balance Assets:Bank:Current   940.00 NZD
    │ ─────────────────────────┬─────
    │                                           ╰───── accumulated 935.00 NZD, error 5.00 NZD
──╯
2020-01-28              1000.00 NZD
2020-02-01  -50.00 NZD   950.00 NZD  Food
2020-02-02  -15.00 NZD   935.00 NZD  Drinks
```

## Logging

If the environment variable `LIMABEAN_HARVEST_LOG` is defined, that will accumulate JSON format structured logging, best viewed with a JSON log viewer such as [hl](https://github.com/pamburus/hl).

## Troubleshooting hulling

Hulling programs may also be run directly.  For example:

```
kiri> hull-ofx ./test-cases/kiwibank-ofx1/kiwibank.ofx | jq
[
  {
    "hdr": {
      "ofxheader": "100",
      "version": "102",
      "acctid": "99-1234-0123456-07",
      "curdef": "NZD",
      "balamt": "150.42"
      "dtasof": "20250412",
    },
    "txns": [
      {
        "fitid": "31Mar2025.1",
        "trntype": "CREDIT",
        "dtposted": "20250331",
        "trnamt": "4.72",
        "name": "INTEREST EARNED",
        "memo": "INTEREST EARNED ;"
      },
      {
        "dtposted": "20250331",
        "trnamt": "-10.00",
        "trntype": "DEBIT",
        "name": "WIKIMEDIA 877-600-9454",
        "fitid": "31Mar2025.2",
        "memo": "WIKIMEDIA 877-600-9454 ;"
      }
    ]
  }
]

kiri> hull-csv ./test-cases/first-direct-csv/10-9999-0000001-02.csv | jq
[
  {
    "hdr": {},
    "txns": [
      {
        "date": "10/02/2025",
        "description": "TRANSFER FROM CURRENT",
        "amount": "34.28",
        "balance": "134.28"
      }
    ]
  }
]
```
