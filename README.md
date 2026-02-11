# limabean-harvest

This is a new importer and framework for [Beancount](https://github.com/beancount/beancount) using [Rust](https://rust-lang.org) and [Clojure](https://clojure.org/) and the [Lima parser](https://github.com/tesujimath/beancount-parser-lima) with the following features:

- import from CSV or OFX v1 into Beancount format
- lookup primary account for import from OFX `acctid` field
- infer secondary accounts for postings from payees and narrations in existing Beancount file
- construct transaction ID from OFX `acctid` anf `fitid` fields, and reject import of duplicate transactions
- pair up transactions between accounts where both accounts are imported in the same group

- [Features](clj/doc/10-features.md)
- [Installation](clj/doc/20-installation.md)
- [Customisation](clj/doc/30-customisation.md)
- [Diagnostics and Logging](clj/doc/40-diagnostics-and-logging.md)

## Alternatives

`limabean-harvest` is very new.  For now, or perhaps forever, you may be better served by one of the alternatives.

Also, [beancount_reds_importers](https://reds-rants.netlify.app/personal-finance/make-importers-easy-to-write-and-write-lots-of-them/) makes a strong case for code-as-config.  It is possible that the `limabean-harvest` approach of mostly declarative configuration with minimal custom code may be insufficient for more complex needs.  In which case, go and enjoy `beancount_reds_importers`. 😊

- [beancount_reds_importers](https://github.com/redstreet/beancount_reds_importers)
- [beancount-import](https://github.com/jbms/beancount-import)

## License

Licensed under either of

 * Apache License, Version 2.0
   [LICENSE-APACHE](http://www.apache.org/licenses/LICENSE-2.0)
 * MIT license
   [LICENSE-MIT](http://opensource.org/licenses/MIT)

at your option.
