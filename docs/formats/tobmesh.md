# `.tobmesh` - keyframe meshes

A `.tobmesh` file holds one mesh part of an element (an object, a piece of debris or an effect) as an unindexed
triangle list with one or more keyframes. The meshes are in `assets/meshes/`; `elements.json` names the objects'
meshes per part (see [elements-json.md](elements-json.md)); the game's effects (lightning, meteor, blue pulse, and
the debris of lightning and of a destroyed floor) use five meshes by fixed name (see "File names" below). Reader and
writer: `core.io.MeshReader` and `core.io.MeshWriter`, the model `core.model.MeshData`.

## Coordinates

- Right-handed, y up; in the world x points east and z south. One grid field is 1.0 wide.
- The origin is the bottom centre of the field the object stands on.
- Front faces wind counter-clockwise. No normals are stored: the game computes flat normals and tangents per
  triangle and key when it draws the mesh.
- UV (0, 0) is the top-left texel of the texture image (the Direct3D convention of the original files; the game
  does not flip V).

## Layout

Little endian throughout; `int32` is a signed 32-bit integer, `float32` an IEEE 754 single.

| Offset | Type | Content |
|---|---|---|
| 0 | 8 bytes | magic `TOBMESH\0` (ASCII, then a zero byte) |
| 8 | int32 | version (`1`) |
| 12 | int32 | `t`: number of triangles (at least 1) |
| 16 | int32 | `k`: number of keys (at least 1) |
| 20 | `k` × int32 | the keys, strictly ascending, in frames (16 frames = 1 simulation unit) |
| 20 + 4`k` | int32 | flags: bit 0 = vertex colours present; bit 1 = UVs differ between keys; bit 2 = colours differ between keys |
| 24 + 4`k` | `k` blocks | one block per key, in key order (below) |

One key's block:

| Type | Count | Content |
|---|---|---|
| float32 | 9`t` | positions: per triangle its three vertices, each x, y, z |
| float32 | 6`t` | UVs: per triangle its three vertices, each u, v |
| int32 | 3`t` (only if flag bit 0) | vertex colours: per triangle its three vertices, each as ARGB `0xAARRGGBB` |

The file ends right after the last block; a reader rejects trailing bytes. Only bit 0 of the flags changes the
layout; bits 1 and 2 are written as hints and ignored when reading. A mesh with colours has them in every key. The
game averages the vertex colours with the part's tint from `elements.json` (see
[elements-json.md](elements-json.md)).

## Animation

All parts of an element are drawn at the same frame. Between two keys the game interpolates the positions and
colours linearly; the UVs are not interpolated but taken from the next key (as ToB 2005 did). The named animation
ranges (walk, idle, spin, ...) are frame ranges in `elements.json`. A mesh with one key is static.

## Example

The smallest valid file, 88 bytes: one triangle (a 1 × 1 upright triangle facing south), one key at frame 0, no
colours. The UVs map the texture's bottom edge to the base and its top centre to the tip.

```text
00000000  54 4F 42 4D 45 53 48 00  01 00 00 00 01 00 00 00
00000010  01 00 00 00 00 00 00 00  00 00 00 00 00 00 00 BF
00000020  00 00 00 00 00 00 00 00  00 00 00 3F 00 00 00 00
00000030  00 00 00 00 00 00 00 00  00 00 80 3F 00 00 00 00
00000040  00 00 00 00 00 00 80 3F  00 00 80 3F 00 00 80 3F
00000050  00 00 00 3F 00 00 00 00
```

Read as: magic, version 1, 1 triangle, 1 key, key 0, flags 0; positions (-0.5, 0, 0), (0.5, 0, 0), (0, 1, 0);
UVs (0, 1), (1, 1), (0.5, 0).

## File names

The shipped meshes are named `NNNN-name.tobmesh`; `NNNN` is the texture or mesh number of ToB 2005, which the
converter kept, so related files sort together (for example `2000-zapper-n.tobmesh` with
`textures/2000-zapper-color.png`). The game uses the paths written in `elements.json`, plus these five fixed names,
which must stay: `0910-lightning`, `0920-meteor`, `0930-bluepulse`, `0960-expl_lightning`, `1550-expl_floor`
(`.tobmesh`).
