# The element catalogue (`elements.json`, format `tob2026-elements`)

`assets/elements.json` tells the game how each object type looks: its mesh parts with their textures, its
animation ranges, how it breaks apart, and the textures of the floors, lifts, sky and ground. Reader and writer:
`core.io.ElementLibraryReader` and `core.io.ElementLibraryWriter`; the model is `core.model.ElementLibrary`. All
paths are relative to the assets folder (`src/main/resources/assets`). The file is UTF-8 with LF line endings.

## Example

One element, the klondike, and two base textures (the `source` text is shortened):

```json
{
  "format" : "tob2026-elements",
  "version" : 4,
  "elements" : {
    "KLD" : {
      "groups" : {
        "1" : {
          "parts" : [
            {
              "mesh" : "meshes/2500-klondike.tobmesh",
              "texture" : "textures/2500-klondike-color.png",
              "normalMap" : "textures/2501-klondike-normal.png",
              "render" : "OPAQUE",
              "specular" : false,
              "lighting" : "LIT",
              "tint" : "#FFFFFF"
            }
          ]
        }
      },
      "animations" : {
        "spin" : [ 0, 170 ]
      },
      "explosion" : {
        "spriteOffset" : 6,
        "meshes" : [ ]
      },
      "source" : "ToB 2009 ObjectElementKLDRenderer: single group 1; spin 0-170 = grabbing animation"
    }
  },
  "base" : {
    "ground" : {
      "texture" : "textures/9101-ground-color.png"
    },
    "static" : {
      "texture" : "textures/1000-static-color.png",
      "normalMap" : "textures/1001-static-normal.png"
    }
  }
}
```

## Fields

| Field | Type | Meaning |
|---|---|---|
| `format` | string | always `tob2026-elements` |
| `version` | integer | `4`; the reader also reads versions 1-3 (see "Versions") |
| `elements` | object | one entry per object type, keyed by its three-letter code (see [tower-json.md](tower-json.md)) |
| `base` | object | the textures of everything that is not an object, keyed by name (see "Base textures") |

An element:

| Field | Type | Meaning |
|---|---|---|
| `groups` | object | the mesh groups, keyed by their number (1-99) as a string; at least one |
| `groups.N.parts` | array | the group's mesh parts, drawn together |
| `animations` | object | named frame ranges `[from, to]` (16 frames = 1 simulation unit); the names are fixed per object type |
| `explosion.spriteOffset` | integer 0-64 | how high above the element's origin its 12 explosion sprites start, in units of 1/32 field |
| `explosion.meshes` | array of paths | pre-broken debris meshes that replace the first group's parts in order when the element is destroyed; parts beyond them do not explode; empty: the shown parts explode as they are; at most as many as the first group has parts |
| `source` | string | where the definition comes from (free text) |

A part:

| Field | Type | Meaning |
|---|---|---|
| `mesh` | path | a `.tobmesh` file (see [tobmesh.md](tobmesh.md)) |
| `texture` | path | the colour texture (PNG) |
| `normalMap` | path, optional | the normal map (PNG, tangent space with green pointing up, as jME expects; the converter flipped the DirectX-style originals) |
| `render` | `OPAQUE` or `GLASS` | opaque, or alpha-blended glass |
| `specular` | boolean | whether the part shows specular highlights |
| `lighting` | `LIT` or `UNLIT` | lit by the scene's lights, or drawn unlit (texture × vertex colour) |
| `tint` | colour `#RRGGBB` | for a mesh without vertex colours, multiplied with the texture (`#FFFFFF` leaves it unchanged); for a mesh with vertex colours, averaged with each vertex colour, (vertex + tint) / 2 per channel, as ToB 2005 did |

Which group is shown when is decided by the game per object type: for the spiders group 1 faces north or south and
group 2 east or west, the grabber's group 3 is its carried klondikes; most objects have only group 1.

## Base textures

| Name | Used for |
|---|---|
| `static`, `static-top`, `static-side-a`, `static-side-b`, `static-bottom`, `static-detail` | floors and boxes |
| `lift` | the lift platforms |
| `playground` | the terrain around the tower |
| `sky`, `ground` | the sky and the ground |
| `haze` | the ground haze of the blue pulse |
| `fx` | the effect atlas (sprites) |

A base entry has `texture` and an optional `normalMap`.

## Texture variants

For the textures named in `elements.json` and the effect atlas image, the game uses `name-2005.png` instead of
`name.png` when that file exists beside it - except the walkable tops (`static-top`, colour and normal map) and the
detail map (`static-detail`), which always use the file named. The shipped assets have `-2005` variants of the
static textures, the prism glass, the sky and the ground. The GUI atlas, the font and the Classic look's textures
have no variants.

## Versions

| Version | Change |
|---|---|
| 1 | the first version: parts without `specular` (read as `true`) and without `lighting` (read as `LIT`) |
| 2 | `specular` per part |
| 3 | `lighting` per part |
| 4 | `explosion` per element (required) |
