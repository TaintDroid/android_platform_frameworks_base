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

package com.android.internal.telephony;

//import com.android.internal.telephony.*;
import android.os.*;
import android.util.Log;
import java.util.ArrayList;

/**
 * {@hide}
 */
public abstract class IccFileHandler extends Handler {
    
    //from TS 11.11 9.1 or elsewhere
    static protected final int COMMAND_READ_BINARY = 0xb0;
    static protected final int COMMAND_UPDATE_BINARY = 0xd6;
    static protected final int COMMAND_READ_RECORD = 0xb2;
    static protected final int COMMAND_UPDATE_RECORD = 0xdc;
    static protected final int COMMAND_SEEK = 0xa2;
    static protected final int COMMAND_GET_RESPONSE = 0xc0;

    // from TS 11.11 9.2.5
    static protected final int READ_RECORD_MODE_ABSOLUTE = 4;

    //***** types of files  TS 11.11 9.3
    static protected final int EF_TYPE_TRANSPARENT = 0;
    static protected final int EF_TYPE_LINEAR_FIXED = 1;
    static protected final int EF_TYPE_CYCLIC = 3;

    //***** types of files  TS 11.11 9.3
    static protected final int TYPE_RFU = 0;
    static protected final int TYPE_MF  = 1;
    static protected final int TYPE_DF  = 2;
    static protected final int TYPE_EF  = 4;

    // size of GET_RESPONSE for EF's
    static protected final int GET_RESPONSE_EF_SIZE_BYTES = 15;
    static protected final int GET_RESPONSE_EF_IMG_SIZE_BYTES = 10;

    // Byte order received in response to COMMAND_GET_RESPONSE
    // Refer TS 51.011 Section 9.2.1
    static protected final int RESPONSE_DATA_RFU_1 = 0;
    static protected final int RESPONSE_DATA_RFU_2 = 1;

    static protected final int RESPONSE_DATA_FILE_SIZE_1 = 2;
    static protected final int RESPONSE_DATA_FILE_SIZE_2 = 3;

    static protected final int RESPONSE_DATA_FILE_ID_1 = 4;
    static protected final int RESPONSE_DATA_FILE_ID_2 = 5;
    static protected final int RESPONSE_DATA_FILE_TYPE = 6;
    static protected final int RESPONSE_DATA_RFU_3 = 7;
    static protected final int RESPONSE_DATA_ACCESS_CONDITION_1 = 8;
    static protected final int RESPONSE_DATA_ACCESS_CONDITION_2 = 9;
    static protected final int RESPONSE_DATA_ACCESS_CONDITION_3 = 10;
    static protected final int RESPONSE_DATA_FILE_STATUS = 11;
    static protected final int RESPONSE_DATA_LENGTH = 12;
    static protected final int RESPONSE_DATA_STRUCTURE = 13;
    static protected final int RESPONSE_DATA_RECORD_LENGTH = 14;

    
    //***** Events

    /** Finished retrieving size of transparent EF; start loading. */
    static protected final int EVENT_GET_BINARY_SIZE_DONE = 4;
    /** Finished loading contents of transparent EF; post result. */
    static protected final int EVENT_READ_BINARY_DONE = 5;
    /** Finished retrieving size of records for linear-fixed EF; now load. */
    static protected final int EVENT_GET_RECORD_SIZE_DONE = 6;
    /** Finished loading single record from a linear-fixed EF; post result. */
    static protected final int EVENT_READ_RECORD_DONE = 7;
    /** Finished retrieving record size; post result. */
    static protected final int EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE = 8;
    /** Finished retrieving image instance record; post result. */
    static protected final int EVENT_READ_IMG_DONE = 9;
    /** Finished retrieving icon data; post result. */
    static protected final int EVENT_READ_ICON_DONE = 10;

    
    //***** Abstract Methods
    
    protected abstract void loadEFLinearFixed(int fileid, int recordNum, Message onLoaded);
    
    protected abstract void loadEFImgLinearFixed(int recordNum, Message onLoaded);
    
    protected abstract void getEFLinearRecordSize(int fileid, Message onLoaded);
    
    protected abstract void loadEFLinearFixedAll(int fileid, Message onLoaded);
    
    protected abstract void loadEFTransparent(int fileid, Message onLoaded);
    
    protected abstract void loadEFImgTransparent(int fileid, int highOffset, int lowOffset,
            int length, Message onLoaded);
    
    protected abstract void updateEFLinearFixed(int fileid, int recordNum, byte[] data,
            String pin2, Message onComplete);
    
    protected abstract void updateEFTransparent(int fileid, byte[] data, Message onComplete);
}
