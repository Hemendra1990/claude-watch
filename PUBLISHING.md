# Publishing Claude Watch

Two channels: **GitHub Releases** (instant) and **JetBrains Marketplace** (reviewed).

## Secrets / signing material

Signing keys live in `signing/` (gitignored — never commit):

| File | Purpose |
|------|---------|
| `signing/chain.crt` | Self-signed certificate chain (valid to 2036) |
| `signing/private.pem` | Encrypted RSA-4096 private key (PKCS#8) |
| `signing/.password` | The key password (local convenience only) |

**Back these up offline.** Marketplace pins your certificate: every future update
must be signed with the **same** key. Lose it → you cannot update the listing.

Build inputs (env vars or `-P` Gradle properties):

| Variable | Gradle property | Value |
|----------|-----------------|-------|
| `PRIVATE_KEY_PASSWORD` | `signing.password` | contents of `signing/.password` |
| `PUBLISH_TOKEN` | `publish.token` | JetBrains Marketplace token (below) |

## Local commands

```bash
export PRIVATE_KEY_PASSWORD="$(cat signing/.password)"

./gradlew buildPlugin       # unsigned zip
./gradlew signPlugin        # -> build/distributions/claude-watch-<v>-signed.zip
./gradlew verifyPlugin      # JetBrains Plugin Verifier (what Marketplace runs)
PUBLISH_TOKEN=xxxxx ./gradlew publishPlugin   # upload to Marketplace
```

## JetBrains Marketplace — one-time setup

1. Sign in at https://plugins.jetbrains.com with a JetBrains account (the vendor).
2. Create a **vendor profile** if prompted.
3. Get a permanent token: profile ▸ **My Tokens** ▸ generate. Keep it secret.
4. First upload can be done two ways:
   - **Web:** Upload Plugin ▸ pick `claude-watch-<v>-signed.zip`. Or
   - **CLI:** `PUBLISH_TOKEN=<token> ./gradlew publishPlugin`
5. First version is **human-reviewed by JetBrains** (hours–days). Updates after
   approval are near-instant.

### Notes for review
- Plugin ID: `dev.hemendra.claudewatch` (immutable once published).
- The name uses "Claude". Review may ask to rename or to confirm the
  non-affiliation disclaimer (already in the description + NOTICE). If they
  reject the name, change `pluginConfiguration.name` and the `<name>` in
  `plugin.xml`, rebuild, resubmit.

## GitHub Release (CI)

Push a tag `vX.Y.Z`; `.github/workflows/release.yml` builds, signs, attaches the
signed zip + `updatePlugins.xml`, and (if `PUBLISH_TOKEN` secret is set) publishes
to Marketplace.

Required GitHub repo secrets (Settings ▸ Secrets ▸ Actions):

| Secret | From |
|--------|------|
| `CERTIFICATE_CHAIN` | `cat signing/chain.crt` |
| `PRIVATE_KEY` | `cat signing/private.pem` |
| `PRIVATE_KEY_PASSWORD` | `cat signing/.password` |
| `PUBLISH_TOKEN` | JetBrains token (optional; omit to skip Marketplace) |

```bash
git tag v0.1.1 && git push origin v0.1.1
```

## Versioning

Bump `version` in `build.gradle.kts` and add a `## [x.y.z]` section to
`CHANGELOG.md` before each release.
