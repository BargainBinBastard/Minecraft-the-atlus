# The Altus

A NeoForge mod for Minecraft 1.21.1. Each world generates a pantheon of gods with a history of
grudges, alliances, lies, secrets and the occasional deicide. Players dream their way into the
Altus to recover fragments of that history, and the gods take notice.

## Status

**Slice 3: memories and the Tome.** Every world generates its history from its seed, players can
dream their way into the Altus, and the Woods now hold lore to find and write down.

### Dreaming

Sleep in a bed at night with blocks a god likes nearby (within 8 blocks). Each liked block adds a
point for that god and each disliked block takes one away. If one god reaches 4 points, you dream
toward it; if two gods tie for the lead, you dream of the Woods with no focus; if no god reaches 4,
you sleep normally. You can dream once per night.

In the Altus your inventory is stashed and you arrive empty-handed in the Woods. The dream lasts
10 minutes. Dying there just wakes you. When you wake, anything you picked up in the Altus
vanishes and your own things come back. A crash or disconnect mid-dream is repaired on login.

The Altus is a dark forest at sunset, the same shape in every world. At its center is the
Clearing, where four carved **Inscriptions** stand. Each one speaks of the god your dream leans
toward: what it loves, what it hates, its nature, and how it came to be. A dream with no focus
speaks of the god who made the world. A god whose very existence is a secret has had its carvings
scraped away.

### Memories and the Tome

Reading an inscription gives you a memory, but you can't make sense of it in the Altus. When you
wake, your memories become readable and start to fade: the **Fading Memory** effect shows how long
the last one has left (45 minutes by default). A memory that fades is gone, though you can find it
again by dreaming.

To keep a memory, write it in a **Tome**: craft one from a book and quill surrounded by eight
rotten flesh. Open the Tome, press **Write a memory**, and pick one; it is written onto the page
with the day you wrote it. The pages are also yours to write on freely, and nothing marks an
entry as authentic, so you can lie in your Tome if you like. Behind the scenes, the game keeps a
hidden record of each real entry (dropped if you edit the entry away) and of what you have
learned. Those records are what the Altus's gates will check later.

The Mountain, the House, and deeper lore come in the next slices.

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

`<world>/serverconfig/altus-server.toml`: `dreamSeconds` (default 600), `scanRadius` (8),
`scanThreshold` (4) and `fadeMinutes` (45).

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
  dreams toward that god; a second dream the same night is refused; the four inscriptions stand in
  the Clearing; reading them in a dream gives four memories that become readable on waking; one
  is written into a Tome with its hidden record; editing it away drops the record; the rest fade.
- Publishes the build log, test results and the smoke-test history to the `ci-logs` branch.

## Layout

- `io.github.bargainbinbastard.altus.history` — the history engine. Pure Java, no Minecraft code.
- `io.github.bargainbinbastard.altus.lore` — glue between the engine and the running game.
- `io.github.bargainbinbastard.altus.dream` — the Altus dimension, sleeping into it, and waking.
- `tools/` — the original prototype and the parity scripts.
