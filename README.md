# limabean-harvest

This is a new importer and framework for [Beancount](https://github.com/beancount/beancount) using [Rust](https://rust-lang.org) and [Clojure](https://clojure.org/) and the [Lima parser](https://github.com/tesujimath/beancount-parser-lima).

There are existing and mature import frameworks for Beancount, so why build another one?  The differentiating features of limabean-harvest are:

- configuration as data structure not code (but see below for an argument that says this is a misfeature!)
- inference of secondary accounts from payee and narration fields, which in particular enables:
- pairing of transactions between accounts where both accounts are imported in the same group

Transaction pairing is perhaps its most compelling feature, to avoid duplication of transactions when importing from accounts at both ends of a transaction.

See the following pages for further details.

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
