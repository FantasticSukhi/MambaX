/*
 * This file is a part of Telegram X
 * Copyright © 2014 (tgx-android@pm.me)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * File created on 16/11/2023
 */
package org.thunderdog.challegram.component.chat.filter;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.SOURCE)
@IntDef({
  FilterState.VISIBLE,
  FilterState.HIDDEN,
  FilterState.LAST_STATE_VISIBLE,
  FilterState.LAST_STATE_HIDDEN
})
public @interface FilterState {
  int
    VISIBLE = 0,
    HIDDEN = 1,
    LAST_STATE_VISIBLE = 2,
    LAST_STATE_HIDDEN = 3;
}
