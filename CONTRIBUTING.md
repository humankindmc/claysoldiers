# Contributing

Thank you for helping improve ClaySoldiers.

This repository is only for the Clay Soldiers Paper plugin. Do not add unrelated server features, unrelated game modes, or framework code for non-Clay Soldiers gameplay.

## Workflow

- Do not push directly to `main`.
- All changes must go through pull requests.
- Create a feature or fix branch for your work.
- Keep pull requests focused and small when possible.
- Pull requests must pass CI before merge.
- Large gameplay changes should start with an issue or design discussion before implementation.

## Testing

Run the full build before opening or updating a pull request:

```text
./gradlew build
```

On Windows:

```text
.\gradlew.bat build
```

Gameplay changes must include tests where practical, or clear manual test notes in the pull request. Manual notes should explain the Paper version, Java version, commands used, relevant config changes, and observed behavior.

## Code Style

- Keep Java code readable and consistent with the existing style.
- Avoid unnecessary dependencies.
- Keep Paper API compatibility aligned with the project build.
- Keep config and messages user-editable where appropriate.
- Prefer focused changes over broad rewrites.
- Update docs, config comments, or messages when behavior changes.

## Pull Requests

Before requesting review, make sure:

- The branch is up to date with `main`.
- The build passes locally.
- The change is scoped to Clay Soldiers plugin behavior, docs, config, or maintenance.
- Any gameplay impact is documented.
- Any new or changed config/messages remain understandable for server owners.

## Security

Do not report private vulnerabilities in public issues or pull requests. Follow `SECURITY.md` for private reporting instructions.
