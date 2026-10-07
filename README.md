# The Altus

A NeoForge mod for Minecraft 1.21.1. Each world generates a pantheon of gods with a history of
grudges, alliances, lies, secrets and the occasional deicide. Players dream their way into the
Altus to recover fragments of that history, and the gods take notice.

## Status

**Slice 2: the Altus.** Every world generates its history from its seed when the server starts,
and players can now dream their way into the Altus.

### Dreaming

Sleep in a bed at night with blocks a god likes nearby (within 8 blocks). Each liked block adds a
point for that god and each disliked block takes one away. If one god reaches 4 points, you dream
toward it; if two gods tie for the lead, you dream of the Woods with no focus; if no god reaches 4,
you sleep normally. You can dream once per night.

In the Altus your inventory is stashed and you arrive empty-handed in the Woods. The dream lasts
10 minutes. Dying there just wakes you. When you wake, anything you picked up in the Altus
vanishes and your own things come back. A crash or disconnect mid-dream is repaired on login.

The Altus is a dark forest at sunset, the same shape in every world. It is empty for now: the
Mountain, the House, and lore to find come in the next slices.

### Operator commands

These need permission level 2, because they spoil the mystery.

- `/altus history`: a summary of the pantheon.
- `/altus dump`: writes the full history, with sample lore fragments, to
  `<world folder>/altus/history.txt`. Use it to see what each god likes.
- `/altus scan`: how the blocks around you score for each god, and who a sleeper here would
  dream toward.
- `/altus dream [seconds]`: enter the Altus at once, as if asleep where you stand.
- `/altus wake`: leave the Altus at once.

### Settings

`<world>/serverconfig/altus-server.toml`: `dreamSeconds` (default 600), `scanRadius` (8) and
`scanThreshold` (4).

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
- Starts a real dedicated server with the mod and checks, in the running game: the Altus loads
  with solid ground; the bed scan counts every block tag; a mock player dreams and wakes with its
  inventory intact; dying in the Altus wakes it; sleeping in a bed at night beside a god's blocks
  dreams toward that god; a second dream the same night is refused.
- Publishes the build log, test results and the smoke-test history to the `ci-logs` branch.

## Layout

- `io.github.bargainbinbastard.altus.history` — the history engine. Pure Java, no Minecraft code.
- `io.github.bargainbinbastard.altus.lore` — glue between the engine and the running game.
- `io.github.bargainbinbastard.altus.dream` — the Altus dimension, sleeping into it, and waking.
- `tools/` — the original prototype and the parity scripts.
