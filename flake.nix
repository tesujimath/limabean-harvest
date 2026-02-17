{
  description = "limabean-harvest importer for limabean";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    flake-utils.url = "github:numtide/flake-utils";
    autobean-format = {
      url = "github:SEIAROTg/autobean-format";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = inputs:
    inputs.flake-utils.lib.eachDefaultSystem
      (system:
        let
          overlays = [ (import inputs.rust-overlay) ];
          pkgs = import inputs.nixpkgs {
            inherit system;
          };
          pkgs-with-rust-overlay = import inputs.nixpkgs {
            inherit system overlays;
          };
          flakePkgs = {
            autobean-format = inputs.autobean-format.packages.${system}.default;
          };
          # cargo-nightly based on https://github.com/oxalica/rust-overlay/issues/82
          nightly = pkgs-with-rust-overlay.rust-bin.selectLatestNightlyWith (t: t.default);
          cargo-nightly = pkgs.writeShellScriptBin "cargo-nightly" ''
            export RUSTC="${nightly}/bin/rustc";
            exec "${nightly}/bin/cargo" "$@"
          '';

          ci-packages = with pkgs; [
            bashInteractive
            coreutils
            diffutils
            just

            cargo
            gcc

            clojure
            neil
            git
          ];

          version = (builtins.fromTOML (builtins.readFile ./rust/Cargo.toml)).package.version;
          limabean-harvest =
            pkgs.rustPlatform.buildRustPackage
              {
                inherit version;
                pname = "limabean-harvest";

                src = ./rust;

                cargoDeps = pkgs.rustPlatform.importCargoLock {
                  lockFile = ./rust/Cargo.lock;
                };

                meta = with pkgs.lib; {
                  description = "Import framework and importers for Beancount";
                  homepage = "https://github.com/tesujimath/limabean-harvest";
                  license = with licenses; [ asl20 mit ];
                  # maintainers = [ maintainers.tesujimath ];
                };

                propagatedBuildInputs = with pkgs; [
                  clojure
                ];
              };

        in
        with pkgs;
        {
          devShells.default = mkShell {
            nativeBuildInputs = [
              cargo-modules
              cargo-nightly
              cargo-udeps
              cargo-outdated
              cargo-edit
              clippy
              rustc

              jre
              # useful tools:
              beancount
              beanquery
              flakePkgs.autobean-format
            ] ++ ci-packages;

            shellHook = ''
              PATH=$PATH:$(pwd)/scripts.dev:$(pwd)/rust/target/debug

              export LIMABEAN_HARVEST_UBERJAR=$(pwd)/clj/target/limabean-harvest-${version}-standalone.jar
              export LIMABEAN_HARVEST_CLJ_LOCAL_ROOT=$(pwd)/clj
              export LIMABEAN_HARVEST_USER_CLJ=$(pwd)/examples/clj/local.clj
              export LIMABEAN_HARVEST_LOG=$(pwd)/limabean-harvest.log
            '';
          };

          packages.default = limabean-harvest;

          apps = {
            tests = {
              type = "app";
              program = "${writeShellScript "limabean-harvest-tests" ''
                export PATH=${pkgs.lib.makeBinPath ci-packages}:$(pwd)/rust/target/debug
                just test
              ''}";
            };
          };
        }
      );
}
