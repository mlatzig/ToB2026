# ToB 2026 file formats

These documents describe the files of ToB 2026 that players and modders work with, so that others can build towers,
tools or remakes of their own on them. They are written from the code that reads and writes the files
(`src/main/java/de/latzig/game/tob/core/io`); the tests check that each example below is read as shown.

| Document | Files |
|---|---|
| [tower-json.md](tower-json.md) | the tower libraries: `assets/towers/*.json` and your own libraries |
| [tobmesh.md](tobmesh.md) | the keyframe meshes `assets/meshes/*.tobmesh` |
| [elements-json.md](elements-json.md) | the element catalogue `assets/elements.json` |
| [gui-atlas-and-font.md](gui-atlas-and-font.md) | the GUI atlas `assets/gui/gui-atlas.json` with its image, and the bitmap font `assets/gui/tob-font.fnt` |

## Conventions

- The assets folder is `src/main/resources/assets`; every path inside a file is relative to it and uses `/`.
- JSON files are UTF-8 with LF line endings. Each starts with `format` and `version`; a reader refuses a format or
  version it does not know. Colours are `#RRGGBB` unless a document says otherwise.
- Textures are PNG files; sounds are WAV files; the music is Ogg Vorbis.

## Not documented

The effect atlas (`assets/fx/fx-atlas.json`), the sound catalogue (`assets/sounds/sounds.json`), the Classic look's
area table (an input of the asset converter, which is not published), and the settings, profiles and recordings in
the user data folder are not documented here: they are internal and may still change.
