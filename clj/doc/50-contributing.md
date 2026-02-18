# Contributing

Contributions are welcome, especially for additional institutions.

The intention is that field mappings for institutions be collected in the [(example) config file](../../test-cases/first-direct-csv/config.edn) for the test for that institution (which requires an example import file).
The point is that usage of such configuration is left to the end-user, who simply copies what they need into their personal config.

Any institution-specific realizer functions are collected in [API contrib](../src/limabean/harvest/api/contrib).  Note that these are not always needed.
Namespaces for realizer functions should be named `<institution>-<format>` for maximal clarity and in case of multiple formats existing for an institution.

Everything in the `limabean.harvest.api` namespace is essentially frozen, since these may be referenced in user's config files, and changes would cause breakage.  Additional functions named with version-suffixes are always compatible.

See the [existing test cases](../../test-cases) for what institutions are already supported with field mappings, and add your own in PRs.
