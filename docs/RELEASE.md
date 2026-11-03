# Release

How Demand.Indicators is built, packaged and published. Nothing here runs unless a release is asked for: no version bumps, rebuilt jars, tags or published releases for ordinary work. Changelog entry rules are in [CHANGELOG.md](CHANGELOG.md); code owners are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Pieces

| Piece | Path | Role |
|---|---|---|
| Changelog | `Changelog` | Source of the version and the release notes. The newest version heading is the release |
| Mod metadata | `mod_info.json` | Version stamped into the ZIP's copy; loads `jars/demandindicators.jar` |
| Version file | `demandIndicators.version` | Online Version Checker file on `master`, registered in `data/config/version/version_files.csv`; stamped at packaging, committed back after a release |
| Release builder | `jars/src/demandindicators/tools/ReleaseBuild.java` | `build` and `package`, run with `java <file>` |
| Builder checks | `jars/src/demandindicators/tools/ReleaseBuildChecks.java` | Headless checks of changelog parsing, stamping, file filtering and asset references |
| Build libraries | `lib/` | `starfarer.api.jar`, `lwjgl.jar`, `lwjgl_util.jar`, `json.jar` and `log4j-1.2.9.jar` from game `0.98a-RC8`, and `LunaLib.jar`, all copied from Industrial.Evolution's `lib/` |
| Release jar | `jars/release/demandindicators.jar` | Built by `build`, gitignored, runtime classes only; shipped as `jars/demandindicators.jar` |
| Release workflow | `.github/workflows/release.yml` | Manual only: preview without a version, publish with one |

The IDE artifact (`jars/demandindicators.jar`, gitignored) is separate from the release jar, so an IDE build never replaces it.

The release jar is built in the workflow from `lib/` and never committed, as in Tidy.Panels. Do not add the house template's committed jar and source hash check.

## Versions

Versions use `major.minor.letter`: the first number for a major release, the second for changes players will notice, the letter for fixes and small patches. Reset the lower parts when a higher one changes. The newest `Changelog` section is marked `- UNRELEASED` until it ships.

## Build the release jar

From the repository root, with JDK 17:

```text
java jars/src/demandindicators/tools/ReleaseBuild.java build
```

- Compiles against the jars in `lib/`. No game install is needed. `starfarer_obf.jar` and the Kotlin runtime are not needed: the code uses only API types and `LunaSettings.getBoolean`.
- Builds into a fresh `out/release-build-*`, keeps only runtime classes (the `tools` package never ships), and replaces the release jar only after a clean compile. The jar must contain `demandindicators/plugins/ModPlugin.class`.
- After a game or LunaLib update, copy the new jars into `lib/`. Never use a Maven JSON jar: the game's `JSONException` is checked and `build` refuses a jar where it is not.

## Package

```text
java jars/src/demandindicators/tools/ReleaseBuild.java package          # preview
java jars/src/demandindicators/tools/ReleaseBuild.java package 1.0.g    # release
```

- Needs a release jar from `build`.
- Preview packages the newest changelog section.
- Release requires the version (`1.0.g`, no `v`) to equal the newest changelog heading, and that section must not be `UNRELEASED`.
- Ships Git-tracked files only: `mod_info.json`, `demandIndicators.version`, `Changelog`, credit files, `data/` (`csv json shader ship wpn proj system variant skin`), `graphics/` (`png jpg jpeg`), `sounds/` (`ogg wav`) and the release jar as `jars/demandindicators.jar`. Source, `lib/`, docs, IDE, agent and workflow files, and any path under a dot, `unused`, `unedited`, `tools` or `docs` directory are left out.
- Fails if `data/config/settings.json` or `data/config/LunaSettings.csv` is missing, or if a `graphics/ui/` or `graphics/icon/` path named in `data/` is not packaged. The mod's art predates the mod prefix and sits in folders vanilla also uses, so a reference to a vanilla `graphics/ui/` file in `data/` would fail this check; `ASSET_DIRS` in `ReleaseBuild` sets the folders.
- Output in `out/release/`: `Demand.Indicators-<version>.zip` (one `Demand.Indicators/` folder, sorted, fixed timestamps), `release-notes.md`, the stamped version file and `version.txt`. Releases up to `1.0.f` were uploaded by hand as `Demand.Indicators<version>.zip`; the stamped `directDownloadURL` follows the new name.

## Publish

1. Finish the newest `Changelog` section and remove `UNRELEASED`.
2. Run `build` and `package <version>` locally as the preview check, then merge to `master`. Work happens on `dev` for now, so merge `dev` into `master` first.
3. Start the Release workflow on `master` with `version` set: Actions tab, Release, Run workflow, or `gh workflow run release.yml --ref master -f version=<version>`.
4. The workflow runs the builder checks, builds the jar, packages, creates the release and its tag on the packaged commit, then commits the stamped `demandIndicators.version` to `master`.
5. Check the release ZIP and notes, and that `master`'s version file names the new version.

The workflow has no push, pull request, tag or schedule trigger. Without a version it only uploads a preview artifact. It refuses to overwrite an existing release, and run on an existing version tag it publishes that tag. It compiles from `lib/` and downloads nothing.

## Maintenance

Update this guide when `ReleaseBuild`, `ReleaseBuildChecks`, `.github/workflows/release.yml`, the version file or the jars in `lib/` change.
