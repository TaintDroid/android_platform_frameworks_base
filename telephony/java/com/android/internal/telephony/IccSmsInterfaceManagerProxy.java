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

//TODO remove after moving SmsRawData and ISms.Stub to telephony
import com.android.internal.telephony.gsm.*;

public class IccSmsInterfaceManagerProxy extends ISms.Stub {
    private IccSmsInterfaceManager mIccSmsInterfaceManager;

    public IccSmsInterfaceManagerProxy(IccSmsInterfaceManager 
            iccSmsInterfaceManager) {
        this.mIccSmsInterfaceManager = iccSmsInterfaceManager;
        ServiceManager.addService("isms", this);
    }

    public boolean
    //TODO Investigate
    updateMessageOnSimEf(int index, int status, byte[] pdu) throws android.os.RemoteException {
         return mIccSmsInterfaceManager.updateMessageOnSimEf(index, status, pdu);
    }

    //TODO Investigate
    public boolean copyMessageToSimEf(int status, byte[] pdu,
            byte[] smsc) throws android.os.RemoteException {
        return mIccSmsInterfaceManager.copyMessageToSimEf(status, pdu, smsc);
    }

    //TODO Investigate
    public List<SmsRawData> getAllMessagesFromSimEf() throws android.os.RemoteException {
        return mIccSmsInterfaceManager.getAllMessagesFromSimEf();
    }

    //TODO Investigate
    public void sendRawPdu(byte[] smsc, byte[] pdu, PendingIntent sentIntent,
            PendingIntent deliveryIntent) throws android.os.RemoteException {
        mIccSmsInterfaceManager.sendRawPdu(smsc, pdu, sentIntent, 
                deliveryIntent);
    }

    //TODO Investigate
    public void sendMultipartText(String destinationAddress, String scAddress, 
            List<String> parts, List<PendingIntent> sentIntents, 
            List<PendingIntent> deliveryIntents) throws android.os.RemoteException {
        mIccSmsInterfaceManager.sendMultipartText(destinationAddress, scAddress, 
                parts, sentIntents, deliveryIntents);
    }

}
