# Installation

Firstly, the [Clojure CLI](https://clojure.org/reference/clojure_cli) is required to be installed separately, and `clojure` must be on the user's path.

Once the binaries `limabean-harvest`, `limabean-digest`, `hull-csv`, and `hull-ofx` are on the path, the corresponding `limabean-harvest` Clojure code is downloaded automatically on first run from [Clojars](https://clojars.org/io.github.tesujimath/limabean-harvest/).

Options for installing these binaries:

1. Tarballs and zipfiles are provided for each [GitHub release](https://github.com/tesujimath/limabean-harvest/releases) for Linux, macOS, and Windows

2. If you have a Rust toolchain installed, `cargo install limabean-harvest` will install the required binaries into `~/.cargo/bin`.  Add this directory to your path before running `limabean-harvest`

3. If you have Nix, `limabean-harvest` is available as a Nix flake at `url = "github:tesujimath/limabean-harvest"`, and this flake pulls in the Clojure CLI tools automatically

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
