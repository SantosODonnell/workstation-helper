# Workstation Helper

A client-side Minecraft mod (Fabric) that adds vanilla-style helper panels to workstations. It runs only in your own game, so there is no need to install it on the server.

The mod itself supports English and Russian.

## Features

- **Anvil** – enchantment planner: pick the enchantments you want, and the mod calculates the cheapest order to combine your books and guides you step by step.
- **Smithing Table** – armor customizer with a live preview (material, color, trim) and a hint showing where to find smithing templates.
- **Cartography Table** – small tips.
- **Enchanting Table** – 3D view of the bookshelves around the table: how many of them work and what is blocking the rest.
- **Loom** – ready-made banners and your own saved designs, built step by step, with pattern hints and banner copying.
- **Brewing Stand** – searchable potion list, brewing steps and small tips.
- **Stonecutter** – a recipe book like the crafting table's.
- **Furnace, Smoker, Blast Furnace** – a "Fuel" tab showing how many items each fuel smelts.
- **Grindstone** – a tip explaining what it can do.
- **Villagers and Wandering Trader** – an "All possible trades" panel listing trades for every profession level.

Every panel can be hidden with the "−/+" button in the corner of its screen. The whole mod can be turned off in Options → Accessibility → "Helper".

## Screenshots

**Anvil: enchantment planner.** Choose the enchantments you want (incompatible ones are greyed out), press "Calculate", and the mod finds the cheapest order to merge your books, guides you step by step and shows the total level cost.

![Anvil: enchantment planner](docs/screenshots/1-anvil.png)

**Loom: ready-made banners, step by step.** Pick a banner on the left (including your own saved designs), and the panel on the right shows every step in order: which dye and which pattern. The mod places the items into the loom for you.

![Loom: ready-made banners, step by step](docs/screenshots/2-loom.png)

**Smithing Table: armor customizer.** Choose the armor piece, dye color, trim pattern and trim color, and see the result on the preview model right away. The "?" button shows where to find each smithing template.

![Smithing Table: armor customizer](docs/screenshots/3-smithing.png)

**Brewing Stand: every potion, step by step.** Pick a potion from the searchable list to see all ingredients in the right order. Toggle modifiers at the bottom: longer, stronger, splash or lingering.

![Brewing Stand: every potion, step by step](docs/screenshots/4-brewing.png)

**Cartography Table: quick tips.** What each item does: paper zooms out the map, an empty map copies it, and a glass pane locks it.

![Cartography Table: quick tips](docs/screenshots/5-cartography.png)

**Villagers: all possible trades.** Every trade a villager can offer, grouped by profession level, before you even level them up. Works for all professions and the Wandering Trader.

![Villagers: all possible trades](docs/screenshots/6-villager-trades.png)

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar for your game version from the [Releases](../../releases) page.
3. Put the jar into your `mods` folder.

| Minecraft | Mod file | Fabric Loader | Fabric API |
|---|---|---|---|
| 26.3 | `workstation-helper-1.7.0+mc26.3.jar` | 0.19.5+ | 0.163.0+26.3 |
| 26.2 | `workstation-helper-1.7.0+mc26.2.jar` | 0.19.3+ | 0.158.0+26.2 |

Requires Java 25.

## Building from source

```
gradlew build
```

The built mod will appear in `build/libs`. The source code in this repository is for Minecraft 26.3.

## License

MIT – see [LICENSE](LICENSE).
