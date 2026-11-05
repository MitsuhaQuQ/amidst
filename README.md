


Amidst
======
## What is Amidst?

Amidst is a tool to display an overview of a Minecraft world, without actually creating it.

Amidst **can**:

* render an overview of a world from a given seed and Minecraft version
* save an image of the map
* use a save game
* display biome information
* display slime chunks
* display end islands
* display the following structures
  * world spawn
  * strongholds
  * villages
  * witch huts
  * pillager outposts
  * jungle temples
  * desert temples
  * igloos
  * abandoned mine shafts
  * ocean monuments
  * ocean ruins
  * shipwrecks
  * buried treasures
  * nether fortresses
  * end cities
  * since Minecraft 1.18 also bastion remnants, nether fossils, ruined portals, ancient cities, trail ruins, trial chambers and abandoned camps, as far as the version has them

### Minecraft 1.18 and later

Since 1.18, Minecraft places biomes with multi-noise and structures with the structure sets of its built-in data pack. For these versions, Amidst loads the data pack the way a server does and asks Minecraft itself for biomes, the world spawn and where structures go, so the map follows the world generation of the selected version. Amidst recognises every release from 1.18 to 1.21.11, 26.1 to 26.3 and the 26.4 snapshots (currently 26.4-snapshot-7) and follows the renames between them; other snapshots load as an unknown version and usually work too. The map shows the surface biomes; cave biomes such as the deep dark are not drawn. Structure icons are at the coordinates that `/locate` reports. Saves of these versions can be opened, but the players of saves since 26.1 are not shown yet.

Minecraft 26.1 and later ship with readable class names. Minecraft 1.18 to 1.21.11 are obfuscated, so the first time one of them is selected, Amidst downloads the obfuscation map that Mojang publishes for it (about 10 MB) and writes a copy of the jar with readable names, which takes a few seconds. The copy is kept in `%LOCALAPPDATA%\Amidst\mapped-jars` on Windows, `~/Library/Caches/Amidst/mapped-jars` on macOS and `~/.cache/amidst/mapped-jars` on Linux, and can be deleted at any time.

Amidst does not ship Minecraft: install the version you want to view with the Minecraft launcher first, and Amidst loads it from there. Two downloads are on the [releases page](https://github.com/MitsuhaQuQ/amidst/releases):

* `amidst-<release>.jar` runs on Windows, macOS and Linux and needs Java 25, like the latest game; it loads the older versions with that Java as well.
* `AMIDST-<release>-windows.zip` needs no Java: unpack it and run `AMIDST.exe`. It ships its own Java runtime. `scripts/package-windows.ps1` builds it after `mvn package`.

Amidst **cannot**:

* display changes that were applied to a save game like
  * changes made by world editors like MCEdit
  * changes made while loading the world in Minecraft
* find individual blocks or mobs like
  * diamond ore
  * cows

## Amidst has found a new home

Amidst was moved to a new location, since Skidoodle aka skiphs is too busy to maintain it. It has also found some new developers. One of them is DrFrankenstone, a.k.a. Treer, who is the developer of AmidstExporter. Skidoodle is still an owner of Amidst and agreed to move the project.

### Links

* [Download](https://github.com/MitsuhaQuQ/amidst/releases) - this fork, with support for Minecraft 1.18 and later
* [Download of the original Amidst](https://github.com/toolbox4minecraft/amidst/releases) - up to Minecraft 1.17
* [FAQ](https://github.com/toolbox4minecraft/amidst/wiki/FAQ)
* [Wiki](https://github.com/toolbox4minecraft/amidst/wiki)
* [Reporting a Bug](https://github.com/toolbox4minecraft/amidst/wiki/Supporting-the-Development#reporting-a-bug) - please report bugs, so we can fix them
* [Requesting a Feature](https://github.com/toolbox4minecraft/amidst/wiki/Supporting-the-Development#requesting-a-feature)
* [Thread in the minecraftforum](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-tools/2970854-amidst-map-explorer-for-minecraft-1-14)
* [Project Page](https://github.com/toolbox4minecraft/amidst)
* [Supporting the Development](https://github.com/toolbox4minecraft/amidst/wiki/Supporting-the-Development)
* [License Text](https://github.com/toolbox4minecraft/amidst/blob/master/LICENSE.txt)

## What is my internet connection used for?

* Amidst **will** use web services provided by Mojang, e.g. to
  * display information about Minecraft versions.
  * display information about players like the name or the skin.
* Amidst **will** check for updates on every start.
* Amidst **will not** track you with Google Analytics, which was the case in older versions.

## Legal Information

* Amidst is **not** owned by or related to Mojang in any way.
* Amidst comes with **absolutely no warranty**.
* Amidst is free and open source software, licensed under the GPLv3.

## Screenshots

These screenshots are created from the seed 24922 using Amidst v4.0 and Minecraft 1.9.

![default](https://raw.githubusercontent.com/wiki/toolbox4minecraft/amidst/screenshots/screenshot_default_24922_default.png)

### The End Dimension

![The End Dimension](https://raw.githubusercontent.com/wiki/toolbox4minecraft/amidst/screenshots/screenshot_default_24922_end.png)

### Biome Highlighter

![Biome Highlighter](https://raw.githubusercontent.com/wiki/toolbox4minecraft/amidst/screenshots/screenshot_default_24922_biome-highlighter.png)

### Grid

![Grid](https://raw.githubusercontent.com/wiki/toolbox4minecraft/amidst/screenshots/screenshot_default_24922_grid.png)

### Slime Chunks

![Slime Chunks](https://raw.githubusercontent.com/wiki/toolbox4minecraft/amidst/screenshots/screenshot_default_24922_slime.png)
