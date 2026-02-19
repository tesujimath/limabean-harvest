# Features

`limabean-harvest` provides the following features:
- import from CSV or OFX into Beancount format
- lookup primary account for import from OFX `acctid` field, or infer from filename
- infer secondary accounts for postings from payees (or narrations) in existing Beancount file
- construct transaction ID from OFX `acctid` anf `fitid` fields, and reject import of duplicate transactions
- pair up transactions between accounts where both accounts are imported in the same group

The intention is that both OFX v1 and v2 import is complete and general purpose.  This also works for QFX.

CSV import, however, requires customising for each financial instituion according to the headers they export in CSV.  This is essentially just field-mapping.

The import process is governed by configuration in [EDN](https://github.com/edn-format/edn), as in [this example](../../test-cases/first-direct-csv/config.edn).

## Import Phases

There are two phases to import.

### Phase 1 - Hulling

Phase 1 import unwraps the container (OFX, CSV, whatever) into generic JSON, using `hull-ofx`, `hull-csv`, et el.

Differences between instituions is handled by Phase 2 configuration, with minimal (but non-zero) use of custom code.

### Phase 2 - Realization

Phase 2 uses a digest of a Beancount file for context, which enables:

- mapping of external account ID to Beancount account name via metadata in `open` directives
- secondary account inference from payee/narration of previous transactions
- merging of the same transaction from both ends when importing both accounts together
- detection of duplicate imports from existing transaction IDs

## Transaction IDs

A transaction ID is allocated to each transaction by the OFX importer, and this is used to avoid re-importing the same transactions subsequently.  The ID is written to the metadata value `txnid`, (the key is configurable).

The transaction ID comprises `acctid.fitid`.

## Secondary account inference

Where there are matches with payees or narrations in the Beancount file providing context, secondary accounts may be inferred.

If any payees are found to match then narrations are ignored, otherwise narrations are used as a fallback.

In case of multiple matches, all are included, along with a count of match occurences.

## Transaction pairing

When money is moved between accounts, the import file for each account contains a record of the transaction, which leads to a duplicate transaction traditionally requiring manual elimination.

`limabean-harvest` has heuristics to pair up transactions which are imported in the same group, removing the need for this manual step.

An example of paired transactions may be seen in the [pairing golden test output](../../test-cases/pairing/expected.beancount).  Evidence of pairing is the presence of metadata `txnid2`, `payee2`, and `narration2`, which come from the other side of the paired transaction.

Pairing requires secondary account inference to have allocated a unique candidate account.

Pairing is performed only where the source and destination accounts and the value match, and the date is within some configurable threshold (default 3 days).
The result is a single transaction with both `txnid` and `txnid2` metadata values, or a comment in the case of import files missing transaction IDs. The payee and narration from the second transaction are also preserved as `payee2` and `narration2` metadata fields.  These fields are used for account inference in subsequent imports.

If pairing is not happening the most likely explanation is multiple inferred secondary accounts.  The mitigation for this is to fix any references to these payees/narrations in the Beancount file, so that only one secondary account is inferred, for both ends of the candidate paired transaction.

The pairing window may be adjusted in the configuration with e.g. `:pairing {:window 4}` or switched off altogether with `:pairing nil`.  A pairing window of 0 means that transactions to be paired must be for the same day.
