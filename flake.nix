{
  description = "limabean-harvest importer for limabean";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = inputs:
    inputs.flake-utils.lib.eachDefaultSystem
      (system:
        let
          overlays = [ (import inputs.rust-overlay) ];
          pkgs = import inputs.nixpkgs {
            inherit system overlays;
          };
          # cargo-nightly based on https://github.com/oxalica/rust-overlay/issues/82
          nightly = pkgs.rust-bin.selectLatestNightlyWith (t: t.default);
          cargo-nightly = pkgs.writeShellScriptBin "cargo-nightly" ''
            export RUSTC="${nightly}/bin/rustc";
            exec "${nightly}/bin/cargo" "$@"
          '';


          ci-packages = with pkgs; [
            bashInteractive
            coreutils
            diffutils
            just

            rust-bin.stable.latest.default
            gcc

            clojure
          ];

          limabean-harvest =
            let cargo = builtins.fromTOML (builtins.readFile ./rust/Cargo.toml);
            in pkgs.rustPlatform.buildRustPackage
              {
                pname = "limabean-harvest";
                version = cargo.package.version;

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
              gdb

              clojure-lsp
              neil
              jre
            ] ++ ci-packages;

            shellHook = ''
              PATH=$PATH:$(pwd)/scripts.dev:$(pwd)/rust/target/debug
            '';
          };

          packages.default = limabean-harvest;

          apps = {
            tests = {
              type = "app";
              program = "${writeShellScript "limabean-harvest-tests" ''
                export PATH=${pkgs.lib.makeBinPath (ci-packages ++ [limabean-harvest])}
                just test
              ''}";
            };
          };
        }
      );
}
