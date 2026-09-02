<p align="center">
  <img src="docs/public/images/blockengine-icon.png" alt="BlockEngine icon" width="96" height="96">
</p>

# BlockEngine

BlockEngine is a Paper plugin for creating custom blocks that behave like server-side blocks while rendering through generated Minecraft resource packs. It supports Java-backed block systems for plugin developers and data-driven block packs for creators who want to add textured, placeable blocks with JSON and assets only.

The project is designed around a practical workflow: define blocks, generate the resource pack, register creative inventory entries, place blocks in-world, and let BlockEngine handle persistence, item conversion, display management, and runtime events.

## Documentation

The hosted documentation lives on GitHub Pages:

**https://autoyt.github.io/BlockEngine/**

Useful starting points:

- [How BlockEngine works](https://autoyt.github.io/BlockEngine/concepts/how-it-works/)
- [Create a data-driven block pack](https://autoyt.github.io/BlockEngine/guides/create-a-data-pack/)
- [Register blocks from Java](https://autoyt.github.io/BlockEngine/guides/register-blocks-from-java/)
- [Creative inventory system](https://autoyt.github.io/BlockEngine/guides/creative-inventory/)
- [Java API docs](https://autoyt.github.io/BlockEngine/api/)

## What It Does

- Registers custom blocks through a public Java API.
- Loads decorative custom blocks from JSON expansion packs.
- Generates block models, item models, language files, and hosted resource packs.
- Supports catalog and creative-menu exposure with configurable visibility.
- Converts creative placeholder entries into real BlockEngine items when moved into inventories.
- Persists placed custom blocks and routes placement, break, drop, movement, gravity, and display behavior through BlockEngine systems.

## AI Usage

BlockEngine is step-by-step designed by AutoYT, drawing on extensive plugin development experience and hands-on knowledge of Paper plugin architecture.

Much of the implementation was produced with AI assistance, then human reviewed, verified, and tested during development. AI was used as a coding collaborator for iteration speed, refactoring, documentation, and implementation detail work; project direction, feature decisions, debugging feedback, and acceptance testing were guided by AutoYT.

## Releases

Plugin releases are published from `plugin-v*` tags through GitHub Actions:

**https://github.com/Autoyt/BlockEngine/releases**

The API module is also set up for GitHub Packages publishing from `api-v*` tags.

## Building

```bash
./gradlew build
```

Build the plugin jar:

```bash
./gradlew shadowJar
```

Build the documentation site locally:

```bash
cd docs
npm install
npm run build
```

Generate the combined GitHub Pages artifact, including Java docs:

```bash
./gradlew assembleGithubPagesDocs
```

## Project Status

BlockEngine is under active development. APIs and data-pack fields may evolve as the block system, creative inventory integration, and resource-pack generation pipeline continue to mature.
