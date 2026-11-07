# Mirrolit E2E (Maestro)

Maestro flows that drive the real app for the release gate described in `docs/e2e/README.md`.

## Install Maestro

```
brew tap mobile-dev-inc/tap
brew install maestro
maestro --version
```

Requires Java 17+ (the Homebrew cask pulls its own JDK if needed).

## Where flows live

- `e2e/flows/` — Maestro flow YAML files (one journey/case per file or subflow).
- `e2e/flows/_probe.yaml` — a throwaway smoke-check flow for verifying the toolchain against the app; not part of the suite (deleted after use).
- `e2e/.gitignore` — ignores `reports/` (Maestro test-run output) and `books/fetched/` (fixture books downloaded at run time, not committed).

Every flow declares `appId: ${APP_ID}` — never hard-code the package name. Run with:

```
maestro test -e APP_ID=io.mirrolit.app --device <emulator-serial> e2e/flows/<flow>.yaml
```

See `docs/e2e/README.md` for the full release gate, tags and planned runner commands.
