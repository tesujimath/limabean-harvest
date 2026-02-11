# Diagnostics and Logging

## Diagnostics for mismatched balances

A bother when importing transactions is finding that the asserted balance is different from the sum of the postings.

To facilitate resolving such problems, when a balance mismatch is detected, all posts to that account since the
most recent balance assertion are printed along with the error message.

For example:
```
aya> limabean --ledger examples/beancount/balance.beancount
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
