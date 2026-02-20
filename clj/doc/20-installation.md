# Installation

## Packages

### Nix

`limabean-harvest` is available as a Nix flake at `url = "github:tesujimath/limabean-harvest"`, and this flake pulls in the Clojure CLI tools automatically.  It currently runs from Clojars, but is likely in future to run standalone.

### Other packaging

If you have packaged or are interested in packaging `limabean-harvest` for your distro of choice, please reach out for help or at least a mention here.  Thanks!

## Installation modes

There are two ways to run `limabean-harvest`, either standalone or from Clojars.  Running from Clojars means that the required Clojure packages are downloaded by the Clojure runtime on first use.

Selection of runtime is determined by the following:

1. If the environment variable `LIMABEAN_HARVEST_CLJ_LOCAL_ROOT` is defined at runtime, that is the path to local Clojure source, and is used to run the development version using `clojure`, just like [limabean](https://github.com/tesujimath/limabean/blob/main/clj/doc/50-development.md).
2. If the environment variable `LIMABEAN_HARVEST_UBERJAR` is defined at runtime, that is the path to the standalone application jarfile, which is run using `java`
3. If the environment variable `LIMABEAN_HARVEST_UBERJAR` was defined at buildtime, that is the path to the standalone application jarfile, which is run using `java`
4. Otherwise, the application whose version matches `limabean-harvest` is run from Clojars using `clojure`

## Manual Installation

Running from Clojars is recommended for anyone using the [GitHub release](https://github.com/tesujimath/limabean-harvest/releases), that is, not setting any of the environment variables listed above.

## Running from Clojars

Requirements:

1. The [Clojure CLI](https://clojure.org/reference/clojure_cli) is required to be installed separately, and `clojure` must be on the user's path.

2. The Rust binaries `limabean-harvest`, `limabean-digest`, `hull-csv`, and `hull-ofx` must be installed and on the path.

The corresponding `limabean-harvest` Clojure code is downloaded automatically on first run from [Clojars](https://clojars.org/io.github.tesujimath/limabean-harvest/).

Options for installing the Rust binaries:

1. Tarballs and zipfiles are provided for each [GitHub release](https://github.com/tesujimath/limabean-harvest/releases) for Linux, macOS, and Windows

2. If you have a Rust toolchain installed, `cargo install limabean-harvest` will install the required binaries into `~/.cargo/bin`.  Add this directory to your path before running `limabean-harvest`

### macOS

On macOS it is necessary to remove the quarantine attributes after unpacking the tarball, e.g.

```
xattr -rd com.apple.quarantine ./limabean-harvest/bin
```

### Windows

- install [OpenJDK 25 MSI](https://learn.microsoft.com/en-us/java/openjdk/download)
- install [Clojure 1.12 MSI](https://github.com/casselc/clj-msi)
- download limabean-harvest zipfile from GitHub releases and extract somewhere
- add that directory to path

## Standalone

Requirements:

1. Java runtime installed separately, with `java` on the user's path.  Note that the `java.sql` module at least is required, so a minimal jre may be insufficient.

2. The Rust binaries `limabean-harvest`, `limabean-digest`, `hull-csv`, and `hull-ofx` must be installed and on the path.

3. The limabean-harvest standalone jarfile must be available at a location given by the environment variable `LIMABEAN_HARVEST_UBERJAR`

If this environment variable is defined when building the Rust binaries, it is not required at runtime, which is recommended when packaging `limabean-harvest`.

## Building from source

The [`justfile`](../../justfile) has recipes for building from source.

The Rust binaries are built in `rust/target/{release,debug}`, and the jarfiles in `clj/target`.
