# The Altus

A NeoForge mod for Minecraft 1.21.1. Each world generates a pantheon of gods with a history of
grudges, alliances, lies, secrets and the occasional deicide. Players dream their way into the
Altus to recover fragments of that history, and the gods take notice.

## Status

**Slice 1: the history engine.** Every world now generates its history from its seed when the
server starts. There is nothing for players to find yet; that comes with the Altus itself.

Operator commands (permission level 2, because they spoil everything):

- `/altus history` — a summary of the pantheon.
- `/altus dump` — writes the full history, with sample lore fragments, to
  `<world folder>/altus/history.txt`.

## Getting the jar

Every push to `main` is built by GitHub Actions:

1. Open the **Actions** tab and pick the latest green run.
2. Download the `altus-mod-jar` artifact (a zip containing the jar).
3. Put the jar in the `mods` folder of a NeoForge 21.1.x install for Minecraft 1.21.1.

## Building locally

You need JDK 21. Run `./gradlew build`; the jar lands in `build/libs/`.

## What CI checks

- Compiles the mod and runs the unit tests (the history engine is verified against the original
  JavaScript prototype using golden hashes).
- Starts a real dedicated server with the mod, generates a world's history, writes the report,
  and shuts down. If the mod crashes at runtime, the build fails.
- Publishes the build log, test results and the smoke-test history to the `ci-logs` branch.

## Layout

- `io.github.bargainbinbastard.altus.history` — the history engine. Pure Java, no Minecraft code.
- `io.github.bargainbinbastard.altus.lore` — glue between the engine and the running game.
- `tools/` — the original prototype and the parity scripts.
