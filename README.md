# The Altus

A NeoForge mod for Minecraft 1.21.1. Each world generates a pantheon of gods with a history of
grudges, alliances, lies, secrets and the occasional deicide. Players dream their way into the
Altus to recover fragments of that history, and the gods take notice.

## Building

Every push is built by GitHub Actions. Open the **Actions** tab, pick the latest run, and download
the `altus-mod-jar` artifact. Put the jar in the `mods` folder of a NeoForge 21.1.x install.

To build locally you need JDK 21: `./gradlew build`. The jar lands in `build/libs/`.

## Layout

- `io.github.bargainbinbastard.altus.history` — the history engine. Pure Java, no Minecraft code,
  so it is unit-tested on its own.
- Everything else is the mod.
