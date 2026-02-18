# Contributing

Contributions are welcome, especially for additional institutions.

The intention is that institution-specific realizer functions are collected in [API contrib](../src/limabean/harvest/api/contrib), with field mappings defined in the [(example) config file](../../test-cases/first-direct/config.edn) for the test.
Namespaces for realizer functions should be named `<institution>-<format>` for maximal clarity and in case of multiple formats existing for an institution.

Everything in the `limabean.harvest.api` namespace is essentially frozen, since these may be referenced in user's config files, and changes would cause breakage.  Additional functions named with version-suffixes are always compatible.

See the [existing test cases](../../test-cases) for what institutions are already supported with field mappings, and add your own in PRs.
