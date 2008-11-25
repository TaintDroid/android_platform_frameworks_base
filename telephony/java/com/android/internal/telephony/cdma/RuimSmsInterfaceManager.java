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


package com.android.internal.telephony.cdma;

import android.app.PendingIntent;
import android.content.Context;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.ServiceManager;
import android.telephony.gsm.SmsManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.gsm.SmsRawData; //TODO remove after moving to telephony

/**
 * SimSmsInterfaceManager to provide an inter-process communication to
 * access Sms in Sim.
 */
public class RuimSmsInterfaceManager extends IccSmsInterfaceManager {
    static final String LOG_TAG = "CDMA";
    static final boolean DBG = false;

    private CDMAPhone mPhone;
    private final Object mLock = new Object();
    private boolean mSuccess;
    private List<SmsRawData> mSms;

    private static final int EVENT_LOAD_DONE = 1;
    private static final int EVENT_UPDATE_DONE = 2;

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            AsyncResult ar;

            switch (msg.what) {
                case EVENT_UPDATE_DONE:
                    Log.d(LOG_TAG, "Event EVENT_UPDATE_DONE Received"); //TODO
                    ar = (AsyncResult) msg.obj;
                    synchronized (mLock) {
                        mSuccess = (ar.exception == null);
                        mLock.notifyAll();
                    }
                    break;
                case EVENT_LOAD_DONE:
                    Log.d(LOG_TAG, "Event EVENT_LOAD_DONE Received"); //TODO
                    ar = (AsyncResult)msg.obj;
                    synchronized (mLock) {
                        if (ar.exception == null) {
                            mSms  = (List<SmsRawData>)
                                    buildValidRawData((ArrayList<byte[]>) ar.result);
                        } else {
                            if(DBG) log("Cannot load Sms records");
                            if (mSms != null)
                                mSms.clear();
                        }
                        mLock.notifyAll();
                    }
                    break;
            }
        }
    };

    public RuimSmsInterfaceManager(CDMAPhone phone) {
        this.mPhone = phone;
        //ServiceManager.addService("isms", this);
    }

    private void enforceReceiveAndSend(String message) {
        Context context = mPhone.getContext();

        context.enforceCallingPermission(
                "android.permission.RECEIVE_SMS", message);
        context.enforceCallingPermission(
                "android.permission.SEND_SMS", message);
    }

    /**
     * Update the specified message on the RUIM.
     */
    public boolean
    updateMessageOnSimEf(int index, int status, byte[] pdu) {
        //TODO
        mSuccess = false;
        return mSuccess;
    }

    /**
     * Copy a raw SMS PDU to the RUIM.
     */
    public boolean copyMessageToSimEf(int status, byte[] pdu, byte[] smsc) {
        //TODO
        mSuccess = false;
        return mSuccess;
    }

    /**
     * Retrieves all messages currently stored on RUIM.
     */
    public List<SmsRawData> getAllMessagesFromSimEf() {
        //TODO
        return null;
    }

    /**
     * Send a Raw PDU SMS
     */
    public void sendRawPdu(byte[] smsc, byte[] pdu, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        //TODO
    }

    /**
     * Send a multi-part text based SMS.
     */
    public void sendMultipartText(String destinationAddress, String scAddress, List<String> parts,
            List<PendingIntent> sentIntents, List<PendingIntent> deliveryIntents) {
        //TODO
    }

    /**
     * Generates an EF_SMS record from status and raw PDU.
     */
    private byte[] makeSmsRecordData(int status, byte[] pdu) {
        //TODO
        return null;
    }

    /**
     * create SmsRawData lists from all sms record byte[]
     */
    private ArrayList<SmsRawData> buildValidRawData(ArrayList<byte[]> messages) {
        //TODO
        return null;
    }

    private void log(String msg) {
        Log.d(LOG_TAG, "[RuimSmsInterfaceManager] " + msg);
    }
}
