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

package com.android.internal.telephony.gsm;

import com.android.internal.telephony.*; //TODO Remove *
import com.android.internal.telephony.gsm.stk.ImageDescriptor;
import android.os.*;
import android.os.AsyncResult;
import android.os.RegistrantList;
import android.os.Registrant;
import android.util.Log;
import java.util.ArrayList;

/**
 * {@hide}
 */
public final class SIMFileHandler extends IccFileHandler {
    static final String LOG_TAG = "GSM";

    //***** Instance Variables
    GSMPhone phone;


    //***** Inner Classes

    static class LoadLinearFixedContext {
        
        int efid;
        int recordNum, recordSize, countRecords;
        boolean loadAll;

        Message onLoaded;

        ArrayList<byte[]> results;

        LoadLinearFixedContext(int efid, int recordNum, Message onLoaded) {
            this.efid = efid;
            this.recordNum = recordNum;
            this.onLoaded = onLoaded;
            this.loadAll = false;
        }

        LoadLinearFixedContext(int efid, Message onLoaded) {
            this.efid = efid;
            this.recordNum = 1;
            this.loadAll = true;
            this.onLoaded = onLoaded;
        }
 
    }


    //***** Constructor

    SIMFileHandler(GSMPhone phone) {
        this.phone = phone;
    }

    //***** Public Methods

    /**
     * Load a record from a SIM Linear Fixed EF
     *
     * @param fileid EF id
     * @param recordNum 1-based (not 0-based) record number
     * @param onLoaded
     *
     * ((AsyncResult)(onLoaded.obj)).result is the byte[]     
     *  
     */
    protected void loadEFLinearFixed(int fileid, int recordNum, Message onLoaded) {
        Message response 
            = obtainMessage(EVENT_GET_RECORD_SIZE_DONE,
                        new LoadLinearFixedContext(fileid, recordNum, onLoaded));

        phone.mCM.iccIO(COMMAND_GET_RESPONSE, fileid, null,
                        0, 0, GET_RESPONSE_EF_SIZE_BYTES, null, null, response);
    }

    /**
     * Load a image instance record from a SIM Linear Fixed EF-IMG
     * 
     * @param recordNum 1-based (not 0-based) record number
     * @param onLoaded
     * 
     * ((AsyncResult)(onLoaded.obj)).result is the byte[]
     * 
     */
    public void loadEFImgLinearFixed(int recordNum, Message onLoaded) {
        Message response = obtainMessage(EVENT_READ_IMG_DONE,
                new LoadLinearFixedContext(IccConstants.EF_IMG, recordNum,
                        onLoaded));

        phone.mCM.iccIO(COMMAND_GET_RESPONSE, IccConstants.EF_IMG, "img",
                recordNum, READ_RECORD_MODE_ABSOLUTE,
                ImageDescriptor.ID_LENGTH, null, null, response);
    }

    /**
     * get record size for a linear fixed EF
     * 
     * @param fileid EF id
     * @param onLoaded ((AsnyncResult)(onLoaded.obj)).result is the recordSize[]
     *        int[0] is the record length int[1] is the total length of the EF
     *        file int[3] is the number of records in the EF file So int[0] *
     *        int[3] = int[1]
     */
    protected void getEFLinearRecordSize(int fileid, Message onLoaded) {
        Message response
                = obtainMessage(EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE,
                        new LoadLinearFixedContext(fileid, onLoaded));
        phone.mCM.iccIO(COMMAND_GET_RESPONSE, fileid, null,
                    0, 0, GET_RESPONSE_EF_SIZE_BYTES, null, null, response);
    }

    /**
     * Load all records from a SIM Linear Fixed EF
     *
     * @param fileid EF id
     * @param onLoaded
     *
     * ((AsyncResult)(onLoaded.obj)).result is an ArrayList<byte[]>
     *  
     */
    protected void loadEFLinearFixedAll(int fileid, Message onLoaded) {
        Message response = obtainMessage(EVENT_GET_RECORD_SIZE_DONE,
                        new LoadLinearFixedContext(fileid,onLoaded));

        phone.mCM.iccIO(COMMAND_GET_RESPONSE, fileid, null,
                        0, 0, GET_RESPONSE_EF_SIZE_BYTES, null, null, response);
    }

    /**
     * Load a SIM Transparent EF
     *
     * @param fileid EF id
     * @param onLoaded
     *
     * ((AsyncResult)(onLoaded.obj)).result is the byte[]     
     *  
     */

    protected void loadEFTransparent(int fileid, Message onLoaded) {
        Message response = obtainMessage(EVENT_GET_BINARY_SIZE_DONE,
                        fileid, 0, onLoaded);

        phone.mCM.iccIO(COMMAND_GET_RESPONSE, fileid, null,
                        0, 0, GET_RESPONSE_EF_SIZE_BYTES, null, null, response);
    }

    /**
     * Load a SIM Transparent EF-IMG. Used right after loadEFImgLinearFixed to
     * retrive STK's icon data.
     * 
     * @param fileid EF id
     * @param onLoaded
     * 
     * ((AsyncResult)(onLoaded.obj)).result is the byte[]
     * 
     */
    public void loadEFImgTransparent(int fileid, int highOffset, int lowOffset,
            int length, Message onLoaded) {
        Message response = obtainMessage(EVENT_READ_ICON_DONE, fileid, 0,
                onLoaded);

        phone.mCM.iccIO(COMMAND_READ_BINARY, fileid, "img", highOffset, lowOffset,
                length, null, null, response);
    }

    /**
     * Update a record in a linear fixed EF
     * @param fileid EF id
     * @param recordNum 1-based (not 0-based) record number
     * @param data must be exactly as long as the record in the EF
     * @param pin2 for CHV2 operations, otherwist must be null
     * @param onComplete onComplete.obj will be an AsyncResult
     *                   onComplete.obj.userObj will be a IccIoResult on success
     */
    protected void updateEFLinearFixed(int fileid, int recordNum, byte[] data,
            String pin2, Message onComplete) {
        phone.mCM.iccIO(COMMAND_UPDATE_RECORD, fileid, null,
                        recordNum, READ_RECORD_MODE_ABSOLUTE, data.length,
                        IccUtils.bytesToHexString(data), pin2, onComplete);
    }

    /**
     * Update a transparent EF
     * @param fileid EF id
     * @param data must be exactly as long as the EF
     */
    protected void updateEFTransparent(int fileid, byte[] data, Message onComplete) {
        phone.mCM.iccIO(COMMAND_UPDATE_BINARY, fileid, null,
                        0, 0, data.length,
                        IccUtils.bytesToHexString(data), null, onComplete);
    }

    //***** Overridden from Handler

    public void handleMessage(Message msg) {
        AsyncResult ar;
        IccIoResult result;
        Message response = null;
        String str;
        LoadLinearFixedContext lc;

        IccException iccException;
        byte data[];
        int size;
        int fileid;
        int recordNum;
        int recordSize[];

        try {
            switch (msg.what) {
            case EVENT_READ_IMG_DONE:
                ar = (AsyncResult) msg.obj;
                lc = (LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.onLoaded;

                iccException = result.getException();
                if (iccException != null) {
                    sendResult(response, result.payload, ar.exception);
                }
                break;
            case EVENT_READ_ICON_DONE:
                ar = (AsyncResult) msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                iccException = result.getException();
                if (iccException != null) {
                    sendResult(response, result.payload, ar.exception);
                }
                break;
            case EVENT_GET_EF_LINEAR_RECORD_SIZE_DONE:
                ar = (AsyncResult)msg.obj;
                lc = (LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.onLoaded;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();
                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                data = result.payload;

                if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE] ||
                    EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]) {
                    throw new IccFileTypeMismatch();
                }

                recordSize = new int[3];
                recordSize[0] = data[RESPONSE_DATA_RECORD_LENGTH] & 0xFF;
                recordSize[1] = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                       + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);
                recordSize[2] = recordSize[1] / recordSize[0];

                sendResult(response, recordSize, null);
                break;
             case EVENT_GET_RECORD_SIZE_DONE:
                ar = (AsyncResult)msg.obj;
                lc = (LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.onLoaded;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();
                
                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                data = result.payload;
                fileid = lc.efid;
                recordNum = lc.recordNum;

                if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]) {
                    throw new IccFileTypeMismatch();
                }

                if (EF_TYPE_LINEAR_FIXED != data[RESPONSE_DATA_STRUCTURE]) {
                    throw new IccFileTypeMismatch();
                }

                lc.recordSize = data[RESPONSE_DATA_RECORD_LENGTH] & 0xFF;

                size = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                       + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);

                lc.countRecords = size / lc.recordSize;

                 if (lc.loadAll) {
                     lc.results = new ArrayList<byte[]>(lc.countRecords);
                 }

                 phone.mCM.iccIO(COMMAND_READ_RECORD, lc.efid, null,
                         lc.recordNum,
                         READ_RECORD_MODE_ABSOLUTE,
                         lc.recordSize, null, null,
                         obtainMessage(EVENT_READ_RECORD_DONE, lc));
                 break;
            case EVENT_GET_BINARY_SIZE_DONE:
                ar = (AsyncResult)msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();
                
                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                data = result.payload;

                fileid = msg.arg1;

                if (TYPE_EF != data[RESPONSE_DATA_FILE_TYPE]) {
                    throw new IccFileTypeMismatch();
                }

                if (EF_TYPE_TRANSPARENT != data[RESPONSE_DATA_STRUCTURE]) {
                    throw new IccFileTypeMismatch();
                }

                size = ((data[RESPONSE_DATA_FILE_SIZE_1] & 0xff) << 8)
                       + (data[RESPONSE_DATA_FILE_SIZE_2] & 0xff);

                phone.mCM.iccIO(COMMAND_READ_BINARY, fileid, null,
                                0, 0, size, null, null, 
                                obtainMessage(EVENT_READ_BINARY_DONE,
                                              fileid, 0, response));
            break;

            case EVENT_READ_RECORD_DONE:

                ar = (AsyncResult)msg.obj;
                lc = (LoadLinearFixedContext) ar.userObj;
                result = (IccIoResult) ar.result;
                response = lc.onLoaded;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();
                
                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                if (!lc.loadAll) {
                    sendResult(response, result.payload, null);
                } else {
                    lc.results.add(result.payload);

                    lc.recordNum++;

                    if (lc.recordNum > lc.countRecords) {
                        sendResult(response, lc.results, null);
                    } else {                   
                        phone.mCM.iccIO(COMMAND_READ_RECORD, lc.efid, null,
                                    lc.recordNum,
                                    READ_RECORD_MODE_ABSOLUTE,
                                    lc.recordSize, null, null,
                                    obtainMessage(EVENT_READ_RECORD_DONE, lc));
                    }
                }                

            break;
            
            case EVENT_READ_BINARY_DONE:
                ar = (AsyncResult)msg.obj;
                response = (Message) ar.userObj;
                result = (IccIoResult) ar.result;

                if (ar.exception != null) {
                    sendResult(response, null, ar.exception);
                    break;
                }

                iccException = result.getException();
                
                if (iccException != null) {
                    sendResult(response, null, iccException);
                    break;
                }

                sendResult(response, result.payload, null);
            break;

        }} catch (Exception exc) {
            if (response != null) {
                sendResult(response, null, exc);
            } else {
                Log.e(LOG_TAG, "uncaught exception", exc);
            }            
        }
    }

    //***** Private Methods

    private void sendResult(Message response, Object result, Throwable ex) {
        if (response == null) {
            return;
        }

        AsyncResult.forMessage(response, result, ex);

        response.sendToTarget();
    }
}
