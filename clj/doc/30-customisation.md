# Customisation

As described in [features](10-features.md), the import process is in two phases.

1. Hulling

2. Realization

## Hulling

Hulling is responsible for reading the import file into an intermediate format, where each transaction is represented as a Clojure map.  This uses an external program, and more may be provided.

So far two hulling programs are provided:

- `hull-csv` for generic CSV
- `hull-ofx` for both OFX v1 and v2, and also QFX (which seems to be a trivial superset of OFX v2)

Hulling produces a list of hulls, each of which comprises a header and a list of transactions.

Selection of which hulling program to run and how is called classification, and is done on the basis of a path glob in the EDN config, for example:

```
{
  :id :kiwibank-ofx,
  :selector {:path-glob "**kiwibank/*.ofx"
  :ingester ["hull-ofx" :path],
  :hdr {:dialect "kiwibank.ofx"},
}
```

The import path is lower-cased before globbing against the selectors.

`:id` simply identifies the classifier.

`:selector` is what triggers this classifier to be selected.

`:ingester` is a command invocation, where `:path` is substituted by the import path of the file in question.

`:hdr` is optional, and supplements any header fields output by the hulling program in question.  These header fields are used for selection of which realizer to apply.  In general, annotating the header with `:dialect` is the recommended way to ensure the required realizer is chosen.  Note that in the particular case of OFX, the OFX version may be determined from the header produced by `hull-ofx`.

Classifiers are matched in order, so if there are multiple matches, the first one wins.


## Realization

The second phase, realization, formats these intermediate transactions into Beancount format, and is defined by mapping from whichever fields have been extracted from the import and the standard fields, which are as follows.

- `:accid` - account ID, expected to match metadata `accid` for an `open` directive in the context file
- `:cur` - currency
- `:date` - date, in format to be specified
- `:narration`
- `:payee`
- `:txnid`
- `:units`

### Field mapping

Fields may be extracted from any header or transaction field.  Type and date format must also be specified.  Type is optionally one of `:decimal` or `:date`, with omission meaning string.  Dates require an additional `:fmt` parameter,
which is as defined in Java [`DateTimeFormatter.ofPattern`](https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html#patterns).

For example, here is a fragment of the EDN config to extract transactions from OFX1:

```
{
     :txn {:accid {:src :hdr, :key :acctid},
           :cur {:src :hdr, :key :curdef},
           :date {:src :txn, :key :dtposted, :type :date, :fmt "yyyyMMdd"},
           :narration {:src :txn, :key :memo},
           :payee {:src :txn, :key :name},
           :txnid [{:src :hdr, :key :acctid} "." {:src :txn, :key :fitid}],
           :units {:src :txn, :key :trnamt, :type :decimal}},
}
```

In this example:

- the `:accid` field is extracted from the header field `:acctid`.
- the `:narration` field is extracted from the transaction field `:memo`.
- the `:date` field, of type `:date` and format `"yyyyMMdd"`, es extracted from the transaction field `:dtposted`.
- the `:txnid` field is a composite, the concatenation of header field `:acctid` and transaction field `:fitid`, with a separator of `"."`.
- the `:units` field is extracted from the transaction field `:trnamt` and is of type `:decimal`.

None of the field names are magical, so types must be explicitly annotated, except the default type of string.

After this extraction/mapping process, an arbitrary list of functions may be applied to the result.  There are some library functions available, with more to be collated.

### Realizer selection and inheritance

The realizer is selected on the basis of matching header fields from phase 1, in order as with classifiers.  Usually any `:dialect` passed explicitly into Phase 1 in combination with `:ofxheader` is enough.  For example,

```
{
  :base :generic-ofx,
  :id :kiwibank-ofx,
  :selector {:dialect "kiwibank.ofx", :ofxheader "100"},
  :txn-fns [limabean.harvest.api.contrib.kiwibank-ofx/clean-payee-narration]
}
```

In this example, the `:kiwibank-ofx` realizer is based on the already-defined `:generic-ofx` realizer, with a single additional function to customize the mapping of the transaction fields after base realization.

A realizer may be defined relative to one _earlier in the list of realizers_, by referencing its `id` in the field `:base`.  This is useful for customizing OFX import in minor ways without repeating most of the mapping.

### CSVs, inferred accids, and balances

A generic CSV realizer is not possible, and therefore realizers for CSV format are entirely institution-specific, for example for the British bank First direct:

```
{
  :bal {:accid {:src :hdr, :key :inferred-accid},
        :cur {:src :hdr, :key :cur},
        :date {:src :txn, :key :date, :type :date, :fmt "dd/MM/yyyy"},
        :units {:src :txn, :key :balance, :type :decimal}},
  :bal-fns [limabean.harvest.api/inc-date],
  :id :first-direct-csv,
  :selector {:dialect "first-direct.csv"},
  :txn {:accid {:src :hdr, :key :inferred-accid},
        :cur {:src :hdr, :key :cur},
        :date {:src :txn, :key :date, :type :date, :fmt "dd/MM/yyyy"},
        :description {:src :txn, :key :description},
        :units {:src :txn, :key :amount, :type :decimal}},
  :txn-fns [limabean.harvest.api.contrib.first-direct-csv/payee-narration]
}
```

Note that this example illustrates two further points which have not yet been described.

1. The header field `:inferred-accid` is generated before realization and available for use if the import path contains any of the account IDs defined in `accid` metadata in `open` directives in the context file.  In general this is only required if there is no account ID available from hulling.

2. A balance directive may be generated from either the header or individual transactions.  In case of the latter, only the last balance is retained.  (Here the `inc-date` function is used to push the balance onto the next day, since Beancount balance directives apply to the beginning of the day.)

### User provided code

The user may provide their realizer functions via `$LIMABEAN_HARVEST_USER_CLJ`, for example:

```
(ns local
  (:require [clojure.string :as str]))

(defn lowercase-payee
  "Example realizer function to lowercase the payee.  Use in EDN config as local/lowercase-payee"
  [txn]
  (cond-> txn
    (:payee txn) (update :payee str/lower-case)))
```

used like this in the EDN config file:

```
{
     :txn-fns [local/lowercase-payee]}
}
```

## Configuration

The configuration is defined in EDN, passed on the command line with `--config` or via the environment variable `LIMABEAN_HARVEST_CONFIG` (with the command line flag taking priority), and is merged with the [default configuration](../src/limabean/harvest/core/config.clj) in the following way:

- classifiers from default config are appended to those in user config, so applied as a fallback
- realizers from default config are prepended to those in user config, so may be used as a base
- output is deep merged, so individual values may be overridden while keeping the others

See, for example, the [configuration used for the tests](../../test-cases/kiwibank-ofx/config.edn).

`limabean-harvest -v` pretty prints on standard error the result of merging the default and user configurations.
