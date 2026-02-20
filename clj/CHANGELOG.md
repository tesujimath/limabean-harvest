# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [Unreleased]

[commit log]: https://github.com/tesujimath/limabean-harvest/compare/0.2.2...HEAD

## [0.2.2] - 2026-02-20

- replace hull-ofx output dialect with ofxheader and version
- collapse identical realizers for generic-ofx{1,2} down to a single common one
- rename kiwibank-ofx1 as kiwibank-ofx, since version doesn't matter

[commit log]: https://github.com/tesujimath/limabean-harvest/compare/0.2.1...0.2.2

## [0.2.1] - 2026-02-19

### Changed

- limabean.harvest.api.contrib.first-direct renamed as limabean.harvest.api.contrib.first-direct-csv

[commit log]: https://github.com/tesujimath/limabean-harvest/compare/0.2.0...0.2.1

## [0.2.0] - 2026-02-19

### Changed

- hulling protocol now returns a list rather than a single hull
- hull-ofx truncates date/time to yyyyMMdd

### Added

- build uberjar for standalone use #10
- add support for OFX v2 (and QFX)
- limabean-harvest -v pretty prints config on stderr
- improve docs

[commit log]: https://github.com/tesujimath/limabean-harvest/compare/0.1.1...0.2.0

## [0.1.1] - 2026-02-13

First public release
