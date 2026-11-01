<p align="center"><img src="fileosophy-logo.svg" alt="Fileosophy logo" width="160"></p>

# Fileosophy

A personal, trimmed-down file manager for Android with a leather look. Fileosophy is a fork of
[Amaze File Manager](https://github.com/TeamAmaze/AmazeFileManager), cut down to the parts I
actually use.

## What's different from Amaze

- **Share first.** Sharing opens the standard Android share sheet and sits at the top of the menus.
- **Lean menus.** No FTP server, Wi-Fi P2P, compress, encrypt or App Manager entries.
- **Three windows** to move files between.
- **Simple navigation.** The bottom-right button is a Home button that jumps back to the root. The
  3-dot menu has an **Add** section for new folders and files, and the sort button is a pair of
  arrows.
- **Tidy sidebar.** No Quick access item; **History** sits under **Recent files**. The
  Images / Videos / Audio / Documents / APK shortcuts are hidden by default (turn them back on in
  Settings).
- **Sensible defaults.** Drag to select several items, and no delete confirmation. The **Hidden
  Files** manager lives in Settings > Behavior.
- **Launcher shortcuts.** Long-press the icon for Downloads, Home and Recent files.
- **Leather theme.** Saddle-brown toolbars, a cream light theme and an espresso dark theme, with a
  subtle leather grain.
- **Own identity.** Its own name, owl icon and application id (`app.fileosophy.files`), so it
  installs alongside Amaze.

## Download

Get the latest `Fileosophy-<version>.apk` from the
[Releases page](https://github.com/DanielCohen002/fileosophy/releases) and open it on your phone.
You may need to allow installs from your browser or file manager. Google Play Protect can show an
"App scan recommended" prompt because the app isn't from the Play Store: choose **Install without
scanning** (or **Scan app**).

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
