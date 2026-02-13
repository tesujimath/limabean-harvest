# Basic usage

```
kiri> limabean-harvest --config ./test-cases/harvest.edn --context ./test-cases/kiwibank.beancount ./test-cases/kiwibank/*.ofx
2025-03-31 txn "INTEREST EARNED" ""
  txnid: "99-1234-0123456-07.31Mar2025.1"
  Assets:Bank:Current                                                      4.72 NZD
  Income:Unknown

2025-03-31 txn "WIKIMEDIA 877-600-9454" ""
  txnid: "99-1234-0123456-07.31Mar2025.2"
  Assets:Bank:Current                                                    -10.00 NZD
  Expenses:Unknown

2025-04-13 balance Assets:Bank:Current                                   150.42 NZD
```

The config file may be specified in the environment variable `LIMABEAN_HARVEST_CONFIG`, which is recommended.
There is a fallback [default config](../src/limabean/harvest/core/config.clj), which may be enough to import OFX files.

The context file may be specified in the environment variable `LIMABEAN_BEANFILE`, which is also recommended.

So the normal invocation would be as follows

```
kiri> limabean-harvest ./test-cases/kiwibank/*.ofx
```

How to import a file is determined by path globs defined in the configuration, for which see [customisation](30-customisation.md)
