/*
 * Copyright (C) 2006 The Android Open Source Project
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

package android.database;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;

// begin WITH_TAINT_TRACKING
import dalvik.system.Taint;
// end WITH_TAINT_TRACKING

/**
 * Wrapper class for Cursor that delegates all calls to the actual cursor object.  The primary
 * use for this class is to extend a cursor while overriding only a subset of its methods.
 */
public class CursorWrapper implements Cursor {
    /** @hide */
    protected final Cursor mCursor;

    /**
     * Creates a cursor wrapper.
     * @param cursor The underlying cursor to wrap.
     */
    public CursorWrapper(Cursor cursor) {
        mCursor = cursor;
    }

    /**
     * Gets the underlying cursor that is wrapped by this instance.
     *
     * @return The wrapped cursor.
     */
    public Cursor getWrappedCursor() {
        return mCursor;
    }

    public void close() {
        mCursor.close(); 
    }
 
    public boolean isClosed() {
        return mCursor.isClosed();
    }
	
// begin WITH_TAINT_TRACKING
    private int taint_ = Taint.TAINT_CLEAR;

    public void setTaint(int taint) {
        this.taint_ = taint;
    } 
// end WITH_TAINT_TRACKING
	
    public int getCount() {
        return mCursor.getCount();
    }

    public void deactivate() {
        mCursor.deactivate();
    }

    public boolean moveToFirst() {
        return mCursor.moveToFirst();
    }

    public int getColumnCount() {
        return mCursor.getColumnCount();
    }

    public int getColumnIndex(String columnName) {
        return mCursor.getColumnIndex(columnName);
    }

    public int getColumnIndexOrThrow(String columnName)
            throws IllegalArgumentException {
        return mCursor.getColumnIndexOrThrow(columnName);
    }

    public String getColumnName(int columnIndex) {
         return mCursor.getColumnName(columnIndex);
    }

    public String[] getColumnNames() {
        return mCursor.getColumnNames();
    }

    public double getDouble(int columnIndex) {
// begin WITH_TAINT_TRACKING
        return Taint.addTaintDouble(mCursor.getDouble(columnIndex), taint_);
// end WITH_TAINT_TRACKING
    }

    public Bundle getExtras() {
        return mCursor.getExtras();
    }

    public float getFloat(int columnIndex) {
// begin WITH_TAINT_TRACKING
        return Taint.addTaintFloat(mCursor.getFloat(columnIndex), taint_);
// end WITH_TAINT_TRACKING
    }

    public int getInt(int columnIndex) {
// begin WITH_TAINT_TRACKING
        return Taint.addTaintInt(mCursor.getInt(columnIndex), taint_);
// end WITH_TAINT_TRACKING
    }

    public long getLong(int columnIndex) {
// begin WITH_TAINT_TRACKING
        return Taint.addTaintLong(mCursor.getLong(columnIndex), taint_);
// end WITH_TAINT_TRACKING
    }

    public short getShort(int columnIndex) {
// begin WITH_TAINT_TRACKING
        return Taint.addTaintShort(mCursor.getShort(columnIndex), taint_);
// end WITH_TAINT_TRACKING
    }

    public String getString(int columnIndex) {
// begin WITH_TAINT_TRACKING
        String retString = mCursor.getString(columnIndex);	
        if(taint_ != Taint.TAINT_CLEAR) {
            Taint.addTaintString(retString, taint_);
        }
// end WITH_TAINT_TRACKING
        return retString;
    }
    
    public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
        mCursor.copyStringToBuffer(columnIndex, buffer);
    }

    public byte[] getBlob(int columnIndex) {
        return mCursor.getBlob(columnIndex);
    }
    
    public boolean getWantsAllOnMoveCalls() {
        return mCursor.getWantsAllOnMoveCalls();
    }

    public boolean isAfterLast() {
        return mCursor.isAfterLast();
    }

    public boolean isBeforeFirst() {
        return mCursor.isBeforeFirst();
    }

    public boolean isFirst() {
        return mCursor.isFirst();
    }

    public boolean isLast() {
        return mCursor.isLast();
    }

    public int getType(int columnIndex) {
        return mCursor.getType(columnIndex);
    }

    public boolean isNull(int columnIndex) {
        return mCursor.isNull(columnIndex);
    }

    public boolean moveToLast() {
        return mCursor.moveToLast();
    }

    public boolean move(int offset) {
        return mCursor.move(offset);
    }

    public boolean moveToPosition(int position) {
        return mCursor.moveToPosition(position);
    }

    public boolean moveToNext() {
        return mCursor.moveToNext();
    }

    public int getPosition() {
        return mCursor.getPosition();
    }

    public boolean moveToPrevious() {
        return mCursor.moveToPrevious();
    }

    public void registerContentObserver(ContentObserver observer) {
        mCursor.registerContentObserver(observer);   
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        mCursor.registerDataSetObserver(observer);   
    }

    public boolean requery() {
        return mCursor.requery();
    }

    public Bundle respond(Bundle extras) {
        return mCursor.respond(extras);
    }

    public void setNotificationUri(ContentResolver cr, Uri uri) {
        mCursor.setNotificationUri(cr, uri);        
    }

    public void unregisterContentObserver(ContentObserver observer) {
        mCursor.unregisterContentObserver(observer);        
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        mCursor.unregisterDataSetObserver(observer);
    }
}

