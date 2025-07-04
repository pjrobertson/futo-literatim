/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.futo.inputmethod.keyboard;

import android.graphics.Rect;
import android.util.SparseArray;

import org.futo.inputmethod.keyboard.internal.KeyVisualAttributes;
import org.futo.inputmethod.keyboard.internal.KeyboardIconsSet;
import org.futo.inputmethod.keyboard.internal.KeyboardParams;
import org.futo.inputmethod.latin.common.Constants;
import org.futo.inputmethod.latin.common.CoordinateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Loads an XML description of a keyboard and stores the attributes of the keys. A keyboard
 * consists of rows of keys.
 * <p>The layout file for a keyboard contains XML that looks like the following snippet:</p>
 * <pre>
 * &lt;Keyboard
 *         latin:keyWidth="10%p"
 *         latin:rowHeight="50px"
 *         latin:horizontalGap="2%p"
 *         latin:verticalGap="2%p" &gt;
 *     &lt;Row latin:keyWidth="10%p" &gt;
 *         &lt;Key latin:keyLabel="A" /&gt;
 *         ...
 *     &lt;/Row&gt;
 *     ...
 * &lt;/Keyboard&gt;
 * </pre>
 */
public class Keyboard {
    @Nonnull
    public final KeyboardId mId;
    public final int mThemeId;

    /** Total height of the keyboard, including the padding and keys */
    public final int mOccupiedHeight;
    /** Total width of the keyboard, including the padding and keys */
    public final int mOccupiedWidth;

    /** Base height of the keyboard, used to calculate rows' height */
    public final int mBaseHeight;
    /** Base width of the keyboard, used to calculate keys' width */
    public final int mBaseWidth;

    /** left, right, top, bottom specify respective padding */
    public final Rect mPadding;
    /** Default gap between rows */
    public final int mVerticalGap;

    /** Per keyboard key visual parameters */
    public final KeyVisualAttributes mKeyVisualAttributes;

    public final int mMostCommonKeyHeight;
    public final int mMostCommonKeyWidth;

    /** More keys keyboard template */
    public final int mMoreKeysTemplate;

    /** Maximum column for more keys keyboard */
    public final int mMaxMoreKeysKeyboardColumn;

    /** List of keys in this keyboard */
    @Nonnull
    private final List<Key> mSortedKeys;
    @Nonnull
    public final List<Key> mShiftKeys;
    @Nonnull
    public final List<Key> mAltCodeKeysWhileTyping;
    @Nonnull
    public final KeyboardIconsSet mIconsSet;

    private final SparseArray<Key> mKeyCache = new SparseArray<>();

    @Nonnull
    private final ProximityInfo mProximityInfo;
    @Nonnull
    private final KeyboardLayout mKeyboardLayout;

    public final boolean mAutoShift;

    private final boolean mProximityCharsCorrectionEnabled;

    public Keyboard(@Nonnull final KeyboardParams params) {
        mId = params.mId;
        mThemeId = params.mThemeId;
        mOccupiedHeight = params.mOccupiedHeight;
        mOccupiedWidth = params.mOccupiedWidth;
        mBaseHeight = params.mBaseHeight;
        mBaseWidth = params.mBaseWidth;
        mMostCommonKeyHeight = params.mMostCommonKeyHeight;
        mMostCommonKeyWidth = params.mMostCommonKeyWidth;
        mMoreKeysTemplate = params.mMoreKeysTemplate;
        mMaxMoreKeysKeyboardColumn = params.mMaxMoreKeysKeyboardColumn;
        mKeyVisualAttributes = params.mKeyVisualAttributes;
        mPadding = new Rect(params.mLeftPadding, params.mTopPadding, params.mRightPadding, params.mBottomPadding);
        mVerticalGap = params.mVerticalGap;
        mAutoShift = params.mAutoShift;

        mSortedKeys = Collections.unmodifiableList(new ArrayList<>(params.mSortedKeys));
        mShiftKeys = Collections.unmodifiableList(params.mShiftKeys);
        mAltCodeKeysWhileTyping = Collections.unmodifiableList(params.mAltCodeKeysWhileTyping);
        mIconsSet = params.mIconsSet;

        mProximityInfo = new ProximityInfo(params.GRID_WIDTH, params.GRID_HEIGHT,
                mOccupiedWidth, mOccupiedHeight, mMostCommonKeyWidth, mMostCommonKeyHeight,
                mSortedKeys, params.mTouchPositionCorrection);
        mProximityCharsCorrectionEnabled = params.mProximityCharsCorrectionEnabled;
        mKeyboardLayout = KeyboardLayout.newKeyboardLayout(mSortedKeys, mMostCommonKeyWidth,
                mMostCommonKeyHeight, mOccupiedWidth, mOccupiedHeight);
    }

    protected Keyboard(@Nonnull final Keyboard keyboard) {
        mId = keyboard.mId;
        mThemeId = keyboard.mThemeId;
        mOccupiedHeight = keyboard.mOccupiedHeight;
        mOccupiedWidth = keyboard.mOccupiedWidth;
        mBaseHeight = keyboard.mBaseHeight;
        mBaseWidth = keyboard.mBaseWidth;
        mMostCommonKeyHeight = keyboard.mMostCommonKeyHeight;
        mMostCommonKeyWidth = keyboard.mMostCommonKeyWidth;
        mMoreKeysTemplate = keyboard.mMoreKeysTemplate;
        mMaxMoreKeysKeyboardColumn = keyboard.mMaxMoreKeysKeyboardColumn;
        mKeyVisualAttributes = keyboard.mKeyVisualAttributes;
        mPadding = keyboard.mPadding;
        mVerticalGap = keyboard.mVerticalGap;

        mSortedKeys = keyboard.mSortedKeys;
        mShiftKeys = keyboard.mShiftKeys;
        mAltCodeKeysWhileTyping = keyboard.mAltCodeKeysWhileTyping;
        mIconsSet = keyboard.mIconsSet;

        mProximityInfo = keyboard.mProximityInfo;
        mProximityCharsCorrectionEnabled = keyboard.mProximityCharsCorrectionEnabled;
        mKeyboardLayout = keyboard.mKeyboardLayout;
        mAutoShift = keyboard.mAutoShift;
    }

    public boolean hasProximityCharsCorrection(final int code) {
        if (!mProximityCharsCorrectionEnabled) {
            return false;
        }
        // Note: The native code has the main keyboard layout only at this moment.
        // TODO: Figure out how to handle proximity characters information of all layouts.
        final boolean canAssumeNativeHasProximityCharsInfoOfAllKeys = (
                mId.mElementId == KeyboardId.ELEMENT_ALPHABET
                || mId.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED);
        return canAssumeNativeHasProximityCharsInfoOfAllKeys || Character.isLetter(code);
    }

    @Nonnull
    public ProximityInfo getProximityInfo() {
        return mProximityInfo;
    }

    @Nonnull
    public KeyboardLayout getKeyboardLayout() {
        return mKeyboardLayout;
    }

    /**
     * Return the sorted list of keys of this keyboard.
     * The keys are sorted from top-left to bottom-right order.
     * @return the sorted unmodifiable list of {@link Key}s of this keyboard.
     */
    @Nonnull
    public List<Key> getSortedKeys() {
        return mSortedKeys;
    }

    @Nullable
    public Key getKey(final int code) {
        if (code == Constants.CODE_UNSPECIFIED) {
            return null;
        }
        synchronized (mKeyCache) {
            final int index = mKeyCache.indexOfKey(code);
            if (index >= 0) {
                return mKeyCache.valueAt(index);
            }

            for (final Key key : getSortedKeys()) {
                if (key.getCode() == code) {
                    mKeyCache.put(code, key);
                    return key;
                }
            }
            mKeyCache.put(code, null);
            return null;
        }
    }

    public boolean hasKey(@Nonnull final Key aKey) {
        if (mKeyCache.indexOfValue(aKey) >= 0) {
            return true;
        }

        for (final Key key : getSortedKeys()) {
            if (key == aKey) {
                mKeyCache.put(key.getCode(), key);
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return mId.toString();
    }

    /**
     * Returns the array of the keys that are closest to the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the list of the nearest keys to the given point. If the given
     * point is out of range, then an array of size zero is returned.
     */
    @Nonnull
    public List<Key> getNearestKeys(final int x, final int y) {
        // Avoid dead pixels at edges of the keyboard
        final int adjustedX = Math.max(0, Math.min(x, mOccupiedWidth - 1));
        final int adjustedY = Math.max(0, Math.min(y, mOccupiedHeight - 1));
        return mProximityInfo.getNearestKeys(adjustedX, adjustedY);
    }

    @Nonnull
    public int[] getCoordinates(@Nonnull final int[] codePoints) {
        final int length = codePoints.length;
        final int[] coordinates = CoordinateUtils.newCoordinateArray(length);
        for (int i = 0; i < length; ++i) {
            final Key key = getKey(codePoints[i]);
            if (null != key) {
                CoordinateUtils.setXYInArray(coordinates, i,
                        key.getX() + key.getWidth() / 2, key.getY() + key.getHeight() / 2);
            } else {
                CoordinateUtils.setXYInArray(coordinates, i,
                        Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE);
            }
        }
        return coordinates;
    }
}
