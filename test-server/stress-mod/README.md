# Minecraft MultiCore — Stress Test Mod

> Auxiliary mod for stress-testing the server. Spawns entities and generates chunks
> to simulate heavy server load, especially useful for testing the MultiCore mod
> with multiple independent regions.
>
> **This is a TEST mod, not part of the MultiCore project.** It is meant to be
> installed alongside MultiCore on a test server.

## What it does

- Spawns configurable numbers of entities (zombies, villagers, etc.) across multiple chunks
- Forces chunk generation around simulated players
- Creates random-tick-heavy blocks (crops, redstone) to generate tick load
- Has a `/stress` command to control intensity

## How to build

```bash
cd test-server/stress-mod
./gradlew build
```

## How to install

1. Build the mod (or use a pre-built jar)
2. Copy `build/libs/stress-mod-*.jar` to `test-server/server/mods/`
3. Restart the server

## Commands

| Command | Description |
|---------|-------------|
| `/stress spawn <count>` | Spawn `<count>` entities in random chunks |
| `/stress clear` | Remove all stress entities |
| `/stress status` | Show current stress level |
| `/stress chunks <radius>` | Force-generate chunks in a radius around each player |

## Usage with MultiCore

1. Start the server with MultiCore mod
2. Connect 2+ players to different areas
3. Run `/stress spawn 100` to spawn 100 entities
4. Run `/stress chunks 10` to generate 10 chunks around each player
5. Run `/multicore status` and `/spark tps` to measure the impact
6. Compare with vanilla (remove MultiCore, repeat)

## Expected behavior

- With spread-out players, entities in different regions tick in parallel
- The mod should show TPS improvement with MultiCore in AGGRESSIVE mode
- In SAFE mode, the benefit is limited to offloaded work only
