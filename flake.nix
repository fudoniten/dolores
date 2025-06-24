{
  description = "Dolores Personal Assistant";

  inputs = {
    nixpkgs.url = "nixpkgs/nixos-25.05";
    utils.url = "github:numtide/flake-utils";
    helpers = {
      url = "github:fudoniten/fudo-nix-helpers";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs = { self, nixpkgs, utils, helpers, ... }:
    utils.lib.eachDefaultSystem (system:
      let
        inherit (helpers.packages."${system}") mkClojureBin;
        pkgs = nixpkgs.legacyPackages."${system}";
      in {
        packages = rec {
          default = dolores;
          dolores = mkClojureBin {
            name = "org.fudo/dolores";
            primaryNamespace = "dolores.cli";
            src = ./.;
          };
        };

        devShells = rec {
          default = updateDeps;
          updateDeps = pkgs.mkShell {
            buildInputs = with helpers.packages."${system}";
              [ (updateClojureDeps cljLibs) ];
          };
          dolores = pkgs.mkShell {
            buildInputs = [ self.packages."${system}".dolores ];
          };
        };
      }) // {
        nixosModules = rec {
          default = dolores;
          dolores = import ./module.nix self.packages;
        };
      };
}
