# Credits

ToB 2026 is a free, non-commercial remake of *Tower of Babel* by Pete Cooke. This file is the long form of the
credits of ToB 2026 version 1.0.0; the game shows a short form in the Hall of Fame's CREDITS tab and in the main
menu's ticker.

## The original game

*Tower of Babel* was designed and programmed by **Pete Cooke** and published by Rainbird / MicroProse in 1989
(Atari ST, Acorn Archimedes) and 1990 (Amiga). The Amiga version's credits, as listed by the game databases
MobyGames and Lemon Amiga, name Pete Cooke for the coding and Neill Glancy and Pete Cooke for the graphics. The
original towers were designed by Pete Cooke and others, each named in the game's tower library: Chris Newcombe,
Nigel Kenward (named "Legin Drawnek" in the towers), Paul Coppins and Tim Roberts.

In December 2004 Pete Cooke allowed Mario Latzig by e-mail to make a free PC version of the game. He assumed that
MicroProse might still hold rights to the original game, while he had kept the copyright, and expected no objection
to a version that is not sold. ToB 2026 is that free version; his message is summarised here, not published.

ToB 2026 is not affiliated with or endorsed by MicroProse or any later holder of rights to *Tower of Babel*.

## Original tower designs

The original towers are the designs of their authors; the game shows each tower's author in the briefing, as written
in the original data.

| Author (as written in the tower data) | Towers |
|---|---|
| Pete Cooke | 90 of the 117 original towers, the 9 tutorial towers and 17 of the 18 demo towers (one has no author) |
| Chris Newcombe | 9 original towers |
| "Legin Drawnek" (the spelling varies between towers) | 14 original towers |
| Tim Roberts | 2 original towers |
| Axeman Towers Ltd. | 2 original towers |

The towers ship in `src/main/resources/assets/towers/` (`original.json`, `tutorial.json`, `demo.json`). They are
included with Pete Cooke's permission for non-commercial use only and are covered by neither of this project's
licences. Towers you build in the Tower Designer are yours.

## The remake

| Person | Contribution |
|---|---|
| Mario Latzig | ToB 2026 (and its predecessors ToB 2005 and ToB 2009): design, code, 3D models, textures, the bitmap font, sound editing and music |
| Marcus Pantel | Support and Analysis: support since 2004, including help with alternative meshes |
| Chris Newcombe | Tower designs for the original game, and the contact to Pete Cooke in 2004 |

## Music

The three songs in `assets/music/` - **Dreamland**, **Improvi** and **Fadeout** - were composed and produced by
Mario Latzig. They are licensed with the other own assets (CC BY-NC-SA 4.0, `LICENSE-ASSETS`).

## Sounds

The sound effects in `assets/sounds/` come from free sample collections on audio CDs from about 20 to 25 years ago;
Mario Latzig edited them heavily for ToB 2005. The names of the collections are no longer known. If you
recognise a sample and hold rights to it, please open an issue at <https://github.com/mlatzig/ToB2026/issues>: it
will be removed.

## Font

The bitmap font (`assets/gui/tob-font.fnt` and `tob-font_0.png`) is based on a free retro bitmap font of unknown
origin, reworked by Mario Latzig. It is treated as an own asset (CC BY-NC-SA 4.0).

## Third-party software

The game uses these libraries at run time (the complete list of the Maven build's runtime dependencies, including the
transitive ones; the test `CreditsDocTest` compares it with the build's class path):

| Component | Maven artifacts | Version | Licence |
|---|---|---|---|
| jMonkeyEngine | `jme3-core`, `jme3-desktop`, `jme3-lwjgl3`, `jme3-effects`, `jme3-jogg` | 3.9.0-stable | BSD 3-Clause License |
| LWJGL | `lwjgl`, `lwjgl-glfw`, `lwjgl-jawt`, `lwjgl-jemalloc`, `lwjgl-openal`, `lwjgl-opencl`, `lwjgl-opengl` (with their natives for Windows, Linux and macOS) | 3.3.6 | BSD 3-Clause License |
| lwjgl3-awt | `lwjgl3-awt` | 0.2.3 | MIT License |
| j-ogg-vorbis | `j-ogg-vorbis` | 1.0.6 | J-Ogg by Tor-Einar Jarnbjo: "Free and commercial use, modification and redistribution with attribution." |
| Lemur | `lemur`, `lemur-proto` | 1.16.0, 1.13.0 | BSD 3-Clause License |
| Guava | `guava` | 19.0 | Apache License 2.0 |
| Jackson | `jackson-databind`, `jackson-core`, `jackson-annotations` | 2.22.3, 2.22 | Apache License 2.0 |
| SLF4J | `slf4j-api`, `jul-to-slf4j` | 2.0.20 | MIT License |
| Logback | `logback-classic`, `logback-core` | 1.6.5 | GNU LGPL 2.1 (Logback is dual-licensed under EPL 2.0 and LGPL 2.1; ToB 2026 uses it under the LGPL 2.1) |

The LWJGL natives contain these native libraries (versions as built into LWJGL 3.3.6's Windows and Linux natives):

| Native library | In | Licence |
|---|---|---|
| GLFW 3.5.0 | `lwjgl-glfw` natives | zlib License |
| OpenAL Soft 1.24.1 | `lwjgl-openal` natives | GNU LGPL 2.0 or later (source: <https://github.com/kcat/openal-soft/tree/1.24.1>); some parts BSD 3-Clause License and the PFFFT licence |
| jemalloc | `lwjgl-jemalloc` natives | BSD 2-Clause License |
| libffi | `lwjgl` natives | MIT License |
| liburing | `lwjgl` natives for Linux | MIT License |

Notes:

- **j-ogg-vorbis** (jME's OGG Vorbis decoder for the music) is J-Ogg by Tor-Einar Jarnbjo. Its Maven metadata states
  the licence as "Free and commercial use, modification and redistribution with attribution." and points to the full
  terms at <https://github.com/stephengold/j-ogg-all/blob/master/LICENSE>; this section is the attribution.
- **jMonkeyEngine**: the shaders in `src/main/resources/MatDefs/Tob/` are derived from jMonkeyEngine 3.9's material
  definitions and shaders (each file names its original). jMonkeyEngine's licence:

  ```text
  Copyright (c) 2009-2025 jMonkeyEngine
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  * Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the name of 'jMonkeyEngine' nor the names of its contributors
    may be used to endorse or promote products derived from this software
    without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
  TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  ```

- **Jackson**'s jars carry their own licence and notice files, which stay in the jars when the game is packaged;
  so do those of SLF4J, jul-to-slf4j and lwjgl3-awt. Guava's jar carries none: Guava's licence text is
  `licenses/Apache-2.0.txt` in the player packages.
- **OpenAL Soft** and **Logback** are LGPL libraries: they are used unchanged, as separate libraries that can be
  replaced (OpenAL Soft as the shared library inside the `lwjgl-openal` natives jar, Logback as its two jars), and
  their sources are available from their projects: OpenAL Soft 1.24.1 at
  <https://github.com/kcat/openal-soft/tree/1.24.1>, Logback 1.6.5 at <https://github.com/qos-ch/logback/tree/v_1.6.5>.
  The player packages contain the licence texts in `licenses/`: `LGPL-2.0.txt` (OpenAL Soft) and `LGPL-2.1.txt`
  (Logback).
- **Licence texts in the player packages** (`licenses/`, copied unchanged from the projects; their sources and
  checksums are listed in `src/package/licenses/SOURCES.md`): `LWJGL-BSD-3-Clause.txt` (LWJGL),
  `Lemur-BSD-3-Clause.txt` and `Lemur-Simsilica-BSD-3-Clause.txt` (Lemur and Lemur-Proto: the project's licence
  and the one in the headers of their source files), `Apache-2.0.txt` (Guava), `jemalloc-BSD-2-Clause.txt`
  (jemalloc), `libffi-MIT.txt` (libffi), `liburing-MIT.txt` (liburing), `Khronos-MIT.txt` (the Khronos material in
  LWJGL's OpenGL binding), `OpenAL-Soft-BSD-3-Clause.txt` and `OpenAL-Soft-PFFFT.txt` (the parts of OpenAL Soft
  under these licences), `LGPL-2.0.txt` and `LGPL-2.1.txt` (above). jMonkeyEngine's licence is reproduced above.

## Java runtime (in the player packages)

The player packages for Windows and Linux contain a Java runtime made with `jlink` from **Eclipse Temurin
21.0.8+9**, the OpenJDK build of the Eclipse Adoptium project, licensed under the GNU General Public License version
2 with the Classpath Exception. Its source code: <https://github.com/adoptium/jdk21u/tree/jdk-21.0.8+9_adopt>. The
runtime's licence and notice files are in its `legal/` folder in each package (`ToB2026/runtime/legal/` on Windows,
`tob2026/lib/runtime/legal/` on Linux).

## Build and test tools (not shipped)

Used only to build and test the game; not part of what a player runs:

| Component | Maven artifacts | Version | Licence |
|---|---|---|---|
| JUnit | `junit-jupiter`, `junit-jupiter-api`, `junit-jupiter-params`, `junit-jupiter-engine`, `junit-platform-commons`, `junit-platform-engine` | 6.1.3 | Eclipse Public License 2.0 |
| opentest4j | `opentest4j` | 1.3.0 | Apache License 2.0 |
| API Guardian | `apiguardian-api` | 1.1.2 | Apache License 2.0 |
| JSpecify | `jspecify` | 1.0.0 | Apache License 2.0 |
| ArchUnit | `archunit` | 1.5.1 | Apache License 2.0 |

Maven and its plugins are the build tool, not part of the project.
