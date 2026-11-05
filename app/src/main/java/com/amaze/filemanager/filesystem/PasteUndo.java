/*
 * Copyright (C) 2014-2026 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.filesystem;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.amaze.filemanager.asynchronous.asynctasks.DeleteTask;
import com.amaze.filemanager.asynchronous.asynctasks.movecopy.PreparePasteTask;
import com.amaze.filemanager.fileoperations.filesystem.OpenMode;
import com.amaze.filemanager.ui.activities.MainActivity;

import androidx.annotation.Nullable;

/**
 * Remembers the last local copy/move so it can be reverted. Only items that did not already exist
 * in the target are tracked, so undoing never touches files that were there before the paste.
 */
public final class PasteUndo {

  private final boolean move;
  private final String targetPath;
  // name of each pasted item, and the folder it came from
  private final List<String> names = new ArrayList<>();
  private final List<String> originalParents = new ArrayList<>();

  private PasteUndo(boolean move, String targetPath) {
    this.move = move;
    this.targetPath = targetPath;
  }

  /**
   * @return the undo record, or null if this paste can't be undone (non-local files, nothing new)
   */
  @Nullable
  public static PasteUndo create(
      MainActivity activity,
      boolean move,
      OpenMode targetMode,
      String targetPath,
      HybridFileParcelable[] sources) {
    if (targetMode != OpenMode.FILE) return null;

    PasteUndo undo = new PasteUndo(move, targetPath);
    for (HybridFileParcelable source : sources) {
      if (source.getMode() != OpenMode.FILE) return null;
      String name = source.getName(activity);
      if (new File(targetPath, name).exists()) continue;
      undo.names.add(name);
      undo.originalParents.add(source.getParent(activity));
    }
    return undo.names.isEmpty() ? null : undo;
  }

  public String getTargetPath() {
    return targetPath;
  }

  public boolean isMove() {
    return move;
  }

  /** The first pasted item, used to scroll the list to it. */
  public String getFirstName() {
    return names.get(0);
  }

  /** @return how many pasted items are present in the target now */
  public int countPresent() {
    int count = 0;
    for (String name : names) {
      if (new File(targetPath, name).exists()) count++;
    }
    return count;
  }

  /** Deletes the copied items, or moves the moved items back where they came from. */
  public void undo(MainActivity activity) {
    if (move) {
      Map<String, ArrayList<HybridFileParcelable>> byParent = new LinkedHashMap<>();
      for (int i = 0; i < names.size(); i++) {
        File pasted = new File(targetPath, names.get(i));
        if (!pasted.exists()) continue;
        byParent
            .computeIfAbsent(originalParents.get(i), k -> new ArrayList<>())
            .add(new HybridFileParcelable(pasted.getPath()));
      }
      for (Map.Entry<String, ArrayList<HybridFileParcelable>> entry : byParent.entrySet()) {
        new PreparePasteTask(activity)
            .execute(
                entry.getKey(), true, activity.isRootExplorer(), OpenMode.FILE, entry.getValue());
      }
    } else {
      ArrayList<HybridFileParcelable> toDelete = new ArrayList<>();
      for (String name : names) {
        File pasted = new File(targetPath, name);
        if (pasted.exists()) toDelete.add(new HybridFileParcelable(pasted.getPath()));
      }
      new DeleteTask(activity, true).execute(toDelete);
    }
  }
}
