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

package com.amaze.filemanager.ui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * Reports where each touch starts, before any child handles it. The pager does not offer touch
 * listeners while its own swiping is off, which is always the case when windows are side by side.
 */
public class PaneTouchLayout extends ConstraintLayout {

  public interface DownListener {
    void onDown(float x, float y);
  }

  private @Nullable DownListener downListener;

  public PaneTouchLayout(Context context) {
    super(context);
  }

  public PaneTouchLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public PaneTouchLayout(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setDownListener(@Nullable DownListener downListener) {
    this.downListener = downListener;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    if (downListener != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
      downListener.onDown(ev.getX(), ev.getY());
    }
    return super.dispatchTouchEvent(ev);
  }
}
