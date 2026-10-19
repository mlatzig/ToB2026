# The GUI atlas (`gui-atlas.json`, format `tob2026-guiatlas`) and the bitmap font

The menus, the game's panel, the dialogs, the cursor and the Tower Designer's icons are drawn from one image, the GUI
atlas `assets/gui/gui-atlas.png` (1024 × 1024 pixels, RGBA), described by `assets/gui/gui-atlas.json`. The texts
use the bitmap font `assets/gui/tob-font.fnt` with its page `tob-font_0.png`. Reader and writer of the atlas
description: `core.io.GuiAtlasReader` and `core.io.GuiAtlasWriter`; the model is `core.model.GuiAtlas`. The font is
read by jMonkeyEngine's bitmap font loader.

## The atlas description

Example, with one region per kind (the shipped file has 209 regions, 34 colours, 35 geometry values):

```json
{
  "format" : "tob2026-guiatlas",
  "version" : 1,
  "image" : "gui/gui-atlas.png",
  "imageWidth" : 1024,
  "imageHeight" : 1024,
  "regions" : {
    "editor.kld" : {
      "x" : 992,
      "y" : 160,
      "w" : 32,
      "h" : 32
    },
    "gui.cursor_normal" : {
      "x" : 0,
      "y" : 192,
      "w" : 32,
      "h" : 32
    },
    "panel.bar" : {
      "x" : 0,
      "y" : 352,
      "w" : 1024,
      "h" : 32
    }
  },
  "colors" : {
    "button_outset_text" : "#000000",
    "menu_text" : "#000000"
  },
  "geometry" : {
    "button_border_width" : 5,
    "button_height" : 32
  },
  "general" : {
    "font.size" : "20.0",
    "screen.height" : "768",
    "screen.width" : "1024"
  }
}
```

| Field | Type | Meaning |
|---|---|---|
| `format` | string | always `tob2026-guiatlas` |
| `version` | integer | always `1` |
| `image` | path | the atlas image, relative to the assets folder |
| `imageWidth`, `imageHeight` | integer 1-65536 | the image's size in pixels |
| `regions` | object | named rectangles in the image: `x`, `y` (top-left corner, from the image's top-left), `w`, `h`, in pixels; each must lie inside the image |
| `colors` | object | named colours `#RRGGBB` of the ToB 2009 skin (text and face colours) |
| `geometry` | object | named integers of the skin, in pixels (border widths, heights) |
| `general` | object | named strings (the skin's font size and screen size) |

Region names:

| Prefix | Content |
|---|---|
| `gui.` | the ToB 2009 window skin: for each widget kind (`button_inset`, `button_outset`, `group_inset`, `group_outset`, `listbox`, `menu`, `progress`, `textbox`, `window`) nine pieces named `_left_top` ... `_right_bottom` plus `_background` (`menu`, `listbox` and `progress` add a few more, such as `_separator`, `_selected`, `_indicator`; `window` has caption, border, menu and grip pieces instead), which the game stretches as a nine-slice; the cursors `cursor_normal` and `cursor_wait`; the dialog icons `icon_error`, `icon_information`, `icon_question`, `icon_warning` |
| `panel.` | the game panel: the title bar `bar`, the caption strips, and every button once per state (`fire0`, `fire1`, ...) |
| `editor.` | the Tower Designer's icons: one per base element and object code (lower case, e.g. `editor.kld`), and a few more |
| `start.` | the title image of the start screens |

The game looks regions up by name; a renamed or missing region is an error when it is drawn.

## The bitmap font

`tob-font.fnt` is a text file in the AngelCode BMFont text format, which jMonkeyEngine reads. Its first lines (the
first one shortened):

```text
info face="ToB2009" size=32 bold=0 italic=0 charset="" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 ...
common lineHeight=32 base=32 scaleW=512 scaleH=192 pages=1 packed=0
page id=0 file="tob-font_0.png"
chars count=96
char id=32 x=0 y=0 width=22 height=32 xoffset=0 yoffset=0 xadvance=22 page=0 chnl=0
char id=33 x=32 y=0 width=12 height=32 xoffset=0 yoffset=0 xadvance=12 page=0 chnl=0
```

- `common`: every line is 32 pixels high; the page image is 512 × 192 pixels.
- One `char` line per character: the 96 characters with the codes 32-127 (printable ASCII and code 127), in a grid
  of 32 × 32 pixel cells, 16 per row. `x`, `y`, `width`, `height` give the glyph's rectangle in the page (pixels,
  from the top-left), `xoffset` and `yoffset` its placement in the line (always 0 here: every glyph fills its full
  cell height), `xadvance` how far the next character starts.
- The page `tob-font_0.png` is white glyphs on a transparent background; the game tints them.

So all texts are ASCII: the game's texts write (c) for the copyright sign.
