# Usage

`limabean-harvest` requires a Beancount file to provide the context for the import, passed on the command line using `--context` or via the environment variable `LIMABEAN_BEANFILE`.

It is usable with just the default configuration, and in this case is able to import generic OFX (v1 and v2) and QFX.  To import from CSV requires [custom configuration](30-customisation.md) to map from its columns to transaction field names.

When run, the imported directives are printed on standard output, so redirect that to a file, or append to your beanfile directly.

```
kiri> limabean-harvest --config ./test-cases/harvest.edn --context ./test-cases/kiwibank.beancount \
        ./test-cases/kiwibank/*.ofx

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

When running with the default configuration or that specified in the environment variable `LIMABEAN_HARVEST_CONFIG`, and with the context file defined in the environment variable `LIMABEAN_BEANFILE`, the normal invocation would be as follows:

```
kiri> limabean-harvest ./test-cases/kiwibank/*.ofx
```

Files are classified for import by path globs defined in the configuration, for which see [customisation](30-customisation.md).

## Context

The Beancount context file is used for various purposes.

### Accounts

Account IDs may occur explicitly in the import file, for example as is the case with OFX.  Otherwise they may be inferred from the import file path, for example as is necessary for CSV, and are made available to the field mapping in the header field `inferred-accid`.

These account IDs must match those in the Beancount context file, which are defined there by means of `accid` metadata strings on `open` directives, as in [this example](../../test-cases/kiwibank.beancount).  Inference from import file path requires a unique match of account ID against the pathname of the import file.

### Transaction IDs

In cases where it is possible to generate a transaction ID, for example with the OFX importer using the `FITID` field, transaction IDs are attached to imported transactions using the `txnid` metadata field.  (In case of paired transactions, additionally `txnid2` is used.)

These transaction IDs are used for de-duplication in case of re-importing a file.

### Payees and narrations

Payees and narrations are extracted from all transactions in the Beancount context file, and collated by frequency of occurrence.  These are then used for secondary account inference.  Narrations are used only in case of no payee matches, and in general are not expected to be useful for account inference.
The result of secondary account inference is a list of postings, which must be hand-edited (by deleting all superfluous ones).

### Troubleshooting context

Context is extracted as JSON from the Beancount file by `limabean-digest`, and this may be run directly for troubleshooting, for example:

```
kiri> limabean-digest ./test-cases/first-direct.beancount | jq
{
  "accids": {
    "10-9999-0000001-02": "Assets:Bank:Uk:Savings",
    "10-9999-0000001-01": "Assets:Bank:Uk:Current"
  },
  "txnids": [],
  "payees": {
    "TRANSFER TO SAVINGS": {
      "Assets:Bank:Uk:Savings": 1
    },
    "TRANSFER FROM CURRENT": {
      "Assets:Bank:Uk:Current": 1
    }
  },
  "narrations": {}
}
```

## Directory structure and file naming

It is recommended to put files from different institutions into separate directories, and use path globs for hulling selection in the [configuration](30-customisation.md).

In cases where primary account ID inference is required, it is recommended to embed the account name in the file name.
