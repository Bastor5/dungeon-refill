# Dungeon Refill

A client-side Fabric mod for **Minecraft 26.2** that tops up your dungeon items from your sacks when a Hypixel SkyBlock dungeon run starts.

When Mort says *"Here, I found this map when I first entered the dungeon."*, the mod checks your inventory and hotbar for each enabled item (by its SkyBlock ID) and runs `/gfs <ITEM> <missing>` for only what's missing. Commands are spaced 1.5 s apart so Hypixel doesn't rate-limit them.

## Features

- Standalone: reads your inventory directly, no other SkyBlock mod required
- Per-item ON/OFF toggle and target amount
- Add any item by SkyBlock ID (e.g. `DUNGEON_STONE`), remove it again with **X**
- Defaults: Spirit Leap 16, Ender Pearl 16, Superboom TNT 64 (on); Decoy, Inflatable Jerry, Architect's First Draft, Toxic / Twilight Arrow Poison (off)

## Usage

| Command | What it does |
| --- | --- |
| `/dungeonrefill` | Open the settings menu (also available from Mod Menu) |
| `/dungeonrefill now` | Refill right now |
| `/dungeonrefill toggle` | Turn the automatic run-start refill on/off |

Settings are saved in `config/dungeonrefill.json`.

## Versions

| Minecraft | Branch | Download |
| --- | --- | --- |
| 26.2 | `main` | [Releases](https://github.com/Bastor5/dungeon-refill/releases) |
| 26.1.2 | [`mc/26.1.2`](https://github.com/Bastor5/dungeon-refill/tree/mc/26.1.2) | [Releases](https://github.com/Bastor5/dungeon-refill/releases) |

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19+
- Fabric API
- Mod Menu (optional)

## Building

Requires a JDK 25 installed (Gradle finds it automatically).

```bash
./gradlew build
```

The jar is written to `build/libs/`.

## Testing

```bash
./gradlew runClientGameTest
```

Launches a real 26.2 client, creates a singleplayer world, fakes Mort's start message and checks the exact `/gfs` commands sent. It also drives the settings menu with simulated mouse and keyboard input.

## Disclaimer

Automatically sending commands is a grey area under Hypixel's rules. Use at your own risk.

## License

MIT, see [LICENSE](LICENSE).
