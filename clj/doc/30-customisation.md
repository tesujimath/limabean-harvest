# Customisation

The import process is in two stages.

1. Read the file into an intermediate format, where each transaction is represented as a Clojure map

2. Realize these intermediate transactions into Beancount format

The first stage is called hulling, and so far two programs are provided: `hull-csv` for generic CSV, and `hull-ofx` for OFX (with currently only OFX1 being supported).  This produces both a header and a list of transactions.

The second stage, realization, is customized in the config file.  In general, this requires mapping between whichever fields have been extracted from the import and the standard fields, which are as follows.

- `:accid` - account ID, expected to match metadata `accid` for an `open` directive in the context file
- `:cur` - currency
- `:date` - date, in format to be specified
- `:narration`
- `:payee`
- `:txnid`
- `:units`

Fields may be extracted from any header or transaction field.  Type and date format must also be specified.

For example, here is a fragment of the EDN config to extract transactions from OFX1 (a Clojure map):

```
{
     :txn {:accid {:key :acctid, :src :hdr},
           :cur {:key :curdef, :src :hdr},
           :date {:fmt "yyyyMMdd", :key :dtposted, :src :txn, :type :date},
           :narration {:key :memo, :src :txn},
           :payee {:key :name, :src :txn},
           :txnid [{:key :acctid, :src :hdr} "." {:key :fitid, :src :txn}],
           :units {:key :trnamt, :src :txn, :type :decimal}},
}
```

The `:accid` field is extracted from the header field `:acctid`.
The `:narration` field is extracted from the transaction field `:memo`.
The `:date` field, of type `:date` and format `"yyyyMMdd"`, es extracted from the transaction field `:dtposted`.
The `:txnid` field is a composite, the concatenation of header field `:acctid` and transaction field `:fitid`, with a separator of `"."`.
The `:units` field is extracted from the transaction field `:trnamt` and is of type `:decimal`.

None of the field names are magical, so types must be explicitly annotated, except the default type of string.

After this extraction/mapping process, an arbitrary list of functions may be applied to the result.  There are some library functions available, with more to be collated.  Or the user may provide their own via `$LIMABEAN_HARVEST_USER_CLJ`, for example:

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
