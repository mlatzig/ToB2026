# Tower libraries (`*.json`, format `tob2026-towerlib`)

A tower library is one JSON file with an ordered list of towers. The game ships three in `assets/towers/`
(`original.json`, `tutorial.json`, `demo.json`); the Tower Designer saves your own libraries as
`<user data folder>/towers/<name>.json`. Reader and writer: `core.io.TowerLibraryReader` and
`core.io.TowerLibraryWriter`; the game loads libraries with `core.io.TowerLibraryLoader`, which skips a broken tower
instead of the whole file. The model is `core.model.TowerDefinition`.

The files are UTF-8 with LF line endings. The writer keeps a fixed field order and pads the grid tokens into
columns, so files compare well in a diff; the readers accept any field order and any whitespace between tokens.

## Example

A library with one small tower: the grabber has to collect one klondike within 120 seconds; there is a lift up to
level 1.

```json
{
  "format" : "tob2026-towerlib",
  "version" : 1,
  "id" : "example",
  "name" : "Example Towers",
  "source" : "hand-written for docs/formats/tower-json.md",
  "towers" : [
    {
      "id" : "example-000",
      "group" : 0,
      "index" : 0,
      "title" : "First Steps",
      "author" : "Somebody",
      "conditions" : {
        "timeLimit" : 120,
        "timeLimitEnabled" : true,
        "klondikes" : 1,
        "robots" : 0
      },
      "behaviour" : {
        "cameras" : true,
        "minesDestroyFloor" : false,
        "timeBombSpeed" : 1
      },
      "effects" : {
        "lightning" : false,
        "meteor" : false,
        "bluePulse" : false,
        "starDensity" : 2,
        "colourPattern" : 0,
        "skyStyle" : 0
      },
      "moon" : {
        "heading" : 0,
        "type" : 1,
        "x" : 8,
        "y" : 20,
        "rotation" : 1
      },
      "palette" : [ "000", "620", "640", "A60", "660", "884", "CC6", "204", "204",
                    "C06", "E08", "CA0", "EC0", "777", "BBB", "FFF", "680", "460" ],
      "sounds" : {
        "background" : 0,
        "spiderWalk" : true,
        "other" : true
      },
      "lock" : {
        "locked" : false,
        "password" : ""
      },
      "levels" : [
        {
          "level" : 0,
          "rows" : [
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . FLR FLR FLR BOX . . . . . .",
            ". . . . . . FLR:GRB FLR FLR FLR:KLD . . . . . .",
            ". . . . . . LUS FLR FLR FLR . . . . . .",
            ". . . . . . FLR FLR FLR FLR . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . ."
          ]
        },
        {
          "level" : 1,
          "rows" : [
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . LUE FLR . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . .",
            ". . . . . . . . . . . . . . . ."
          ]
        }
      ]
    }
  ]
}
```

## The library

| Field | Type | Meaning |
|---|---|---|
| `format` | string | always `tob2026-towerlib` |
| `version` | integer | always `1`; a reader refuses other formats and versions |
| `id` | string | the library's id, e.g. `original` |
| `name` | string | the name shown to the player |
| `source` | string | where the towers come from (free text) |
| `towers` | array | the towers, in playing order; tower ids must be unique within the file |

## A tower

| Field | Type | Meaning |
|---|---|---|
| `id` | string | unique within the library, not blank; recordings and profiles refer to it |
| `group`, `index` | integer 0-999 | the tower's place in the original's 13 groups of 9 (0-based); your own libraries are played in file order, 9 to a group |
| `title`, `author` | string, at most 62 characters | shown in the briefing; an empty author shows as "Anonymous" |
| `conditions.timeLimit` | integer 0-16383 | seconds; kept as the original value even when the limit is off |
| `conditions.timeLimitEnabled` | boolean | whether the time limit applies |
| `conditions.klondikes` | integer 0-7 | klondikes the grabber must collect |
| `conditions.robots` | integer 0-7 | enemy objects that must be destroyed |
| `behaviour.cameras` | boolean | whether the four cameras are available |
| `behaviour.minesDestroyFloor` | boolean | whether a mine also destroys the floor under it |
| `behaviour.timeBombSpeed` | integer 0-3 | 0 slow, 1 medium, 2 fast, 3 faster |
| `effects.lightning`, `effects.meteor`, `effects.bluePulse` | boolean | lightning, meteors, the blue pulse (ground haze) |
| `effects.starDensity` | integer 0-3 | how many stars the sky shows |
| `effects.colourPattern` | integer 0-7 | the floor colour pattern of the Classic look (0-4; 5-7 draw as 0) |
| `effects.skyStyle` | integer 0-3 | an original Amiga value; has no effect in ToB 2026 |
| `moon.heading` | integer 0-7 | 0-3 north, east, south, west; 4 means no moon (5-7 are never shown) |
| `moon.type` | integer 0-7 | the original moon type (0-5; 6 and 7 look like 0) |
| `moon.x`, `moon.y` | integer 0-511, 0-127 | the original screen position; the game uses x 0-17 and y 0-55 for the moon's place in the sky |
| `moon.rotation` | integer 0-3 | the original turning direction; no effect in ToB 2026 |
| `moon.size` | integer 0-2, optional | Normal, Large, Giant; without it the size follows from the tower's seed |
| `palette` | array of 18 strings | the original Amiga palette, 12-bit colours `RGB` as three hex digits; entries 7 and 8 are the sky's horizon and zenith, 1/3 and 4/6 the two floor colour sets (side, top), 13 and 14 the lift platforms in the Classic look (rim, top), 16 and 17 the ground |
| `sounds.background` | integer 0-3 | 0 none, 1 breath, 2 drone A, 3 drone B |
| `sounds.spiderWalk`, `sounds.other` | boolean | whether the walking and the other sounds play |
| `lock.locked`, `lock.password` | boolean, string of at most 10 characters | the original designer's lock; kept, not used by ToB 2026 |
| `levels` | array | the non-empty levels (see "The grid"); a missing level is empty |
| `remake` | object, optional | the look settings of ToB 2009 (see below) |

### The grid

Each tower is a grid of 16 columns (x, west to east) × 16 rows (y, north to south) × 6 levels (0 = bottom). The
original towers use the 8 × 8 area of columns and rows 4-11 and levels 0-3. Each entry of `levels` has `level` (0-5)
and `rows`: exactly 16 strings, row 0 first, each with exactly 16 tokens separated by whitespace, column 0 first.

A token is a base, optionally followed by `:` and an object code:

| Base | Meaning |
|---|---|
| `.` | empty |
| `FLR` | floor |
| `BOX` | solid box; also the floor of the cell above |
| `LUS` | lift going up: its platform, at the bottom |
| `LUE` | lift going up: the stop marker one level above `LUS` |
| `LDS` | lift going down: its platform, at the top |
| `LDE` | lift going down: the stop marker one level below `LDS` |

| Code | Object | Code | Object |
|---|---|---|---|
| `ZAP` | zapper (spider) | `PRM` | proximity mine |
| `PSH` | pusher (spider) | `TMB` | time bomb |
| `GRB` | grabber (spider) | `BLK` | push block |
| `FZN`, `FZE`, `FZS`, `FZW` | enemy zapper facing north, east, south, west | `REL` | reflector |
| `FPN`, `FPE`, `FPS`, `FPW` | enemy pusher facing north, east, south, west | `PNE`, `PSE`, `PSW`, `PNW` | prism facing north-east, south-east, south-west, north-west |
| `RZN` | rotating zapper | `LZN`, `LZE` | lizard moving north-south, east-west |
| `RPN` | rotating pusher | `KLD` | klondike |
| `WON`, `WOE` | worm moving north-south, east-west | `BFL` | bonus flag |
| `WAT` | watcher | `CVN`, `CVE` | converter, north-south and east-west |
| `HOP` | hopper | `FCU`, `FCD` | forcer up, forcer down |
| `FRZ` | freezer | `WIP` | wiper |
| `EXC` | exchanger | | |

Rules the readers check: each spider at most once; at most 20 objects in a tower within the original 8 × 8 × 4
area, at most 64 in a larger one. The game's loader also refuses a tower with an object on anything but `FLR`,
`LUS` or `LDS` (an object cannot stand on a box, a stop marker or an empty cell, even above a box) or a lift
without its stop marker. Whether a tower can be won (a spider, enough klondikes and enemies, a task) is checked by
the Tower Designer and before one of your own towers starts, not by the readers.

### The look settings of ToB 2009 (`remake`)

The 117 original towers carry the look settings that ToB 2009 added; the Remake look uses them, the Classic look
does not. Colours are `#RRGGBB`.

```json
"remake" : {
  "sunColor" : "#FFFFFF",
  "sunSize" : 3,
  "groundColors" : [ "#004B4B", "#006969" ],
  "groundHeight" : 6,
  "liftColor" : "#FFFFFF",
  "clouds" : false,
  "heavyClouds" : false,
  "pattern" : 0,
  "patternColors" : [ "#A500A5", "#E10000" ]
}
```

| Field | Type | Meaning |
|---|---|---|
| `sunColor` | colour | the sun's colour |
| `sunSize`, `groundHeight` | integer 0-10 | kept from ToB 2009; not used by ToB 2026 |
| `groundColors` | 2 colours | the ground's two colours |
| `liftColor` | colour | the lift platforms |
| `clouds`, `heavyClouds` | boolean | clouds, and whether they are heavy |
| `pattern` | integer 0-7 | the floor pattern: 0 crossed, 1 line by line, 2 crossed in half, 3 one in four, 4 alternated, 5 cross-faded by line, 6 by level, 7 by radius |
| `patternColors` | 2 colours | the pattern's two colours |

Without `remake` the Remake look takes its colours from the palette.

## Checksums

A recording of a solution stores the SHA-256 checksum of its tower: the tower written as compact JSON in the field
order above (`TowerLibraryWriter.towerToString`), UTF-8. Any change to a tower changes its checksum; the game still
replays the recording but reports the difference.
