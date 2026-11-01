<p align="center"><img src="fileosophy-logo.svg" alt="Fileosophy logo" width="160"></p>

# Fileosophy

A personal, trimmed-down file manager for Android with a leather look. Fileosophy is a fork of
[Amaze File Manager](https://github.com/TeamAmaze/AmazeFileManager), cut down to the parts I
actually use.

## What's different from Amaze

- **Share first.** Sharing uses the standard Android share sheet and sits at the top of the menus.
- **Lean menus.** No FTP server, Wi-Fi P2P, compress or encrypt entries; the sidebar and action
  menus are trimmed.
- **Three windows** to move files between.
- **Sensible defaults.** Drag to select several items, no delete confirmation, and the
  Images / Videos / Audio / Documents / APK shortcuts hidden in the sidebar (all can be turned back
  on in Settings).
- **Leather theme.** Saddle-brown toolbars, a cream light theme and an espresso dark theme, with a
  subtle leather grain.
- **Own identity.** Its own name, owl icon and application id (`app.fileosophy.files`), so it
  installs alongside Amaze.

## Download

Grab the latest APK from the [Releases page](https://github.com/DanielCohen002/fileosophy/releases) and install it on your phone
(you may need to allow installs from your browser). Releases are marked pre-release while the app
is still changing.

## Build

```
./gradlew assembleFdroidDebug
```

Needs JDK 17+, the Android SDK, Rust with the Android targets (see `file_operations/`), and
`python3` on the PATH. The APK ends up in `app/build/outputs/apk/fdroid/debug/`.

## Credits and license

Fileosophy is built on Amaze File Manager by Team Amaze and its contributors. Thank you to everyone
who made it.

This project is licensed under the [GNU GPL v3](LICENSE.txt) or later, the same as Amaze.

    Copyright (C) 2014-2018 Arpit Khurana <arpitkh96@gmail.com>
    Copyright (C) 2014-2024 Vishal Nehra <vishalmeham2@gmail.com>
    Copyright (C) 2017-2024 Emmanuel Messulam <emmanuelbendavid@gmail.com>
    Copyright (C) 2018-2024 Raymond Lai <airwave209gt at gmail.com>
    Copyright (C) 2019-2024 Vishnu Sanal T <t.v.s10123 at gmail.com>
    This file is part of Amaze File Manager.
    Amaze File Manager is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
