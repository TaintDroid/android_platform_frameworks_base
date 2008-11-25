/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static com.android.internal.telephony.TelephonyProperties.PROPERTY_SIM_OPERATOR_ISO_COUNTRY;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_SIM_OPERATOR_NUMERIC;
import android.os.AsyncResult;
import android.os.RegistrantList;
import android.os.Registrant;
import android.os.Handler;
import android.os.Message;
import android.telephony.gsm.SmsMessage;
import android.util.Log;
import java.util.ArrayList;

import com.android.internal.telephony.cdma.RuimCard;

import com.android.internal.telephony.IccUtils;
import com.android.internal.telephony.AdnRecord;
import com.android.internal.telephony.AdnRecordCache;
import com.android.internal.telephony.AdnRecordLoader;
import com.android.internal.telephony.gsm.MccTable;
import com.android.internal.telephony.gsm.SimCard;
import com.android.internal.telephony.gsm.SimTlv;


// can't be used since VoiceMailConstants is not public
//import com.android.internal.telephony.gsm.VoiceMailConstants;
import com.android.internal.telephony.IccRecords;
import com.android.internal.telephony.PhoneBase;

/**
 * {@hide}
 */
public final class RuimRecords extends IccRecords {
    static final String LOG_TAG = "CDMA";

    private static final boolean CRASH_RIL = false;

    private static final boolean DBG = true;

    //***** Instance Variables

    CDMAPhone phone;
    RegistrantList recordsLoadedRegistrants = new RegistrantList();
    
    int recordsToLoad;  // number of pending load requests

    AdnRecordCache adnCache;
    
    boolean recordsRequested = false; // true if we've made requests for the sim records

    String imsi_m;
    String mdn = null;  // My mobile number
    String h_sid;
    String h_nid;

    String iccid;

    int mncLength = 0;   // 0 is used to indicate that the value
    // is not initialized
    
    //***** Event Constants

    private static final int EVENT_RUIM_READY = 1;
    private static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 2;
    // TODO: check if needed for CDMA
    //private static final int EVENT_GET_IMSI_DONE = 3;
    
    private static final int EVENT_GET_DEVICE_IDENTITY_DONE = 4;
    private static final int EVENT_GET_ICCID_DONE = 5;
    
    // TODO: find synonyms for CDMA
//    private static final int EVENT_GET_AD_DONE = 9; // Admin data on SIM
//    private static final int EVENT_GET_MSISDN_DONE = 10;
    private static final int EVENT_GET_CDMA_SUBSCRIPTION_DONE = 10;

    private static final int EVENT_UPDATE_DONE = 14;

    // TODO: check if EF_CST should be used instead in CDMA
    //private static final int EVENT_GET_SST_DONE = 17;
    
    // TODO: probably needed in CDMA as well
    private static final int EVENT_GET_ALL_SMS_DONE = 18;
    private static final int EVENT_MARK_SMS_READ_DONE = 19;
    
    // TODO: check for CDMA
    private static final int EVENT_SMS_ON_SIM = 21;
    private static final int EVENT_GET_SMS_DONE = 22;
    
    private static final int EVENT_RUIM_REFRESH = 31;
    
    //***** Constructor

    RuimRecords(CDMAPhone phone) {
        super(phone);        
        this.phone = phone;

        // TODO: additional constructor and implementation needed in AdnRecordCache
        adnCache = new AdnRecordCache(phone);

        recordsRequested = false;  // No load request is made till SIM ready

        // recordsToLoad is set to 0 because no requests are made yet
        recordsToLoad = 0;


        phone.mCM.registerForRUIMReady(this, EVENT_RUIM_READY, null);
        phone.mCM.registerForOffOrNotAvailable(
                        this, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, null);
        // TODO: check if needed
        //phone.mCM.setOnSmsOnSim(this, EVENT_SMS_ON_SIM, null);
        phone.mCM.setOnIccRefresh(this, EVENT_RUIM_REFRESH, null);

        // Start off by setting empty state
        onRadioOffOrNotAvailable();        

    }
    
    public AdnRecordCache getAdnCache() {
        return adnCache;
    }
    
    protected void onRadioOffOrNotAvailable() {
// TODO: missing implementation, maybe some parts will be moved to a super class
//    Example:
//        super.onRadioOffOrNotAvailable();
//
//        phone.setSystemProperty(PROPERTY_LINE1_VOICE_MAIL_WAITING, null);
//        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_NUMERIC, null);
//        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ALPHA, null);
//        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ISO_COUNTRY, null);
//
//        // recordsRequested is set to false indicating that the SIM
//        // read requests made so far are not valid. This is set to
//        // true only when fresh set of read requests are made.
//        recordsRequested = false;
    }

    //***** Public Methods
    public void registerForRecordsLoaded(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        recordsLoadedRegistrants.add(r);

        if (recordsToLoad == 0 && recordsRequested == true) {
            r.notifyRegistrant(new AsyncResult(null, null, null));
        }
    }    

    /** Returns null if RUIM is not yet ready */
    public String getIMSI_M() {
        return imsi_m;
    }

    public String getMdnNumber() {
        return mdn;
    }

    // TODO: change STK to CDMA specific term
    /**
     * Called by STK Service when REFRESH is received.
     * @param fileChanged indicates whether any files changed
     * @param fileList if non-null, a list of EF files that changed
     */
    public void onRefresh(boolean fileChanged, int[] fileList) {
        if (fileChanged) {
            // A future optimization would be to inspect fileList and
            // only reload those files that we care about.  For now,
            // just re-fetch all RUIM records that we cache.
            fetchRuimRecords();
        }
    }    

    // TODO: this is something for the base class, change function name to getICCOperatorNumeric
    /** Returns the 5 or 6 digit MCC/MNC of the operator that
     *  provided the RUIM card. Returns null of RUIM is not yet ready
     */
    String getRUIMOperatorNumeric() {
        if (imsi_m == null) {
            return null;
        }

        if (mncLength != 0) {
            // Length = length of MCC + length of MNC
            // TODO: change spec name
            // length of mcc = 3 (TS 23.003 Section 2.2)
            return imsi_m.substring(0, 3 + mncLength);
        }

        // Guess the MNC length based on the MCC if we don't
        // have a valid value in ef[ad]

        int mcc;

        mcc = Integer.parseInt(imsi_m.substring(0,3));

        return imsi_m.substring(0, 3 + MccTable.smallestDigitsMccForMnc(mcc));
    }

    public void setVoiceCallForwardingFlag(int line, boolean enable) {
    // TODO T: implement or use abstract method
    }
  
    public void setVoiceMailNumber(String alphaTag, String voiceNumber,
            Message onComplete){
        // TODO T: implement or use abstract method
    }
    
    //***** Overridden from Handler
    public void handleMessage(Message msg) {
        AsyncResult ar;

        byte data[];

        boolean isRecordLoadResponse = false;

        try { switch (msg.what) {
            case EVENT_RUIM_READY:
                Log.d(LOG_TAG, "Event EVENT_RUIM_READY Received"); //TODO
                onRuimReady();
            break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                Log.d(LOG_TAG, "Event EVENT_RADIO_OFF_OR_NOT_AVAILABLE Received"); //TODO
                onRadioOffOrNotAvailable();
            break;  
            
            case EVENT_GET_DEVICE_IDENTITY_DONE:
                Log.d(LOG_TAG, "Event EVENT_GET_DEVICE_IDENTITY_DONE Received"); //TODO
                // TODO: might be deleted
            break;

            /* IO events */
            
            // TODO: maybe substituted by RIL_REQUEST_CDMA_SUBSCRIPTION (MDN)
//            case EVENT_GET_MSISDN_DONE:
//                isRecordLoadResponse = true;
//
//                ar = (AsyncResult)msg.obj;
//
//                if (ar.exception != null) {
//                    Log.d(LOG_TAG, "Invalid or missing EF[MSISDN]");
//                    break;
//                }
//
//                adn = (AdnRecord)ar.result;
//
//                msisdn = adn.getNumber();
//                msisdnTag = adn.getAlphaTag();
//
//                Log.d(LOG_TAG, "MSISDN: " + msisdn);
//            break;
            
            case EVENT_GET_CDMA_SUBSCRIPTION_DONE:
                Log.d(LOG_TAG, "Event EVENT_GET_CDMA_SUBSCRIPTION_DONE Received"); //TODO
                ar = (AsyncResult)msg.obj;
                String localTemp[] = (String[])ar.result;
                if (ar.exception != null) {
                    break;
                }
                
                mdn    = localTemp[0];
                h_sid  = localTemp[1];
                h_nid  = localTemp[2];
                      
                Log.d(LOG_TAG, "MDN: " + mdn);
                
            break;

            case EVENT_GET_ICCID_DONE:
                Log.d(LOG_TAG, "Event EVENT_GET_ICCID_DONE Received"); //TODO
                isRecordLoadResponse = true;

                ar = (AsyncResult)msg.obj;
                data = (byte[])ar.result;
                
                if (ar.exception != null) {
                    break;
                }                

                iccid = IccUtils.bcdToString(data, 0, data.length);
            
                Log.d(LOG_TAG, "iccid: " + iccid);

            break;

            case EVENT_UPDATE_DONE:
                Log.d(LOG_TAG, "Event EVENT_UPDATE_DONE Received"); //TODO
                ar = (AsyncResult)msg.obj;
                if (ar.exception != null) {
                    Log.i(LOG_TAG, "RuimRecords update failed", ar.exception);
                }
            break;

            // TODO: handled by SMS use cases
//            case EVENT_GET_ALL_SMS_DONE:
//                isRecordLoadResponse = true;
//
//                ar = (AsyncResult)msg.obj;
//                if (ar.exception != null)
//                    break;
//
//                handleSmses((ArrayList) ar.result);
//                break;
//
//            case EVENT_MARK_SMS_READ_DONE:
//                Log.i("ENF", "marked read: sms " + msg.arg1);
//                break;
//
//
//            case EVENT_SMS_ON_SIM:
//                isRecordLoadResponse = false;
//
//                ar = (AsyncResult)msg.obj;
//
//                int[] index = (int[])ar.result;
//
//                if (ar.exception != null || index.length != 1) {
//                    Log.e(LOG_TAG, "[SIMRecords] Error on SMS_ON_SIM with exp "
//                            + ar.exception + " length " + index.length);
//                } else {
//                    Log.d(LOG_TAG, "READ EF_SMS RECORD index=" + index[0]);
//                    phone.mSIMFileHandler.loadEFLinearFixed(EF_SMS,index[0],obtainMessage(EVENT_GET_SMS_DONE));
//                }
//                break;
//
//            case EVENT_GET_SMS_DONE:
//                isRecordLoadResponse = false;
//                ar = (AsyncResult)msg.obj;
//                if (ar.exception == null) {
//                    handleSms((byte[])ar.result);
//                } else {
//                    Log.e(LOG_TAG, "[SIMRecords] Error on GET_SMS with exp "
//                            + ar.exception);
//                }
//                break;

            // TODO: probably EF_CST should be read instead
//            case EVENT_GET_SST_DONE:
//                isRecordLoadResponse = true;
//
//                ar = (AsyncResult)msg.obj;
//                data = (byte[])ar.result;
//
//                if (ar.exception != null) {
//                    break;
//                }
//
//                //Log.d(LOG_TAG, "SST: " + IccUtils.bytesToHexString(data));
//            break;

            case EVENT_RUIM_REFRESH:
                Log.d(LOG_TAG, "Event EVENT_RUIM_REFRESH Received"); //TODO
                isRecordLoadResponse = false;
                ar = (AsyncResult)msg.obj;
                if (ar.exception == null) {
                    //TODO: handleRuimRefresh((int[])(ar.result));
                }
                break;

        }}catch (RuntimeException exc) {
            // I don't want these exceptions to be fatal
            Log.w(LOG_TAG, "Exception parsing SIM record", exc);
        } finally {        
            // Count up record load responses even if they are fails
            if (isRecordLoadResponse) {
                //TODO: onRecordLoaded();
            }
        }
    }    

    protected void onRecordLoaded() {
        // One record loaded successfully or failed, In either case
        // we need to update the recordsToLoad count
        recordsToLoad -= 1;

        if (recordsToLoad == 0 && recordsRequested == true) {
            onAllRecordsLoaded();
        } else if (recordsToLoad < 0) {
            Log.e(LOG_TAG, "RuimRecords: recordsToLoad <0, programmer error suspected");
            recordsToLoad = 0;
        }
    }
    
    protected void onAllRecordsLoaded() {
        // TODO: implementation is missing
    }
    
    
    //***** Private Methods
    
    private void onRuimReady() {
        /* broadcast intent ICC_READY here so that we can make sure
          READY is sent before IMSI ready
        */
        
        // TODO: broadcastSimStateChangedIntent will probably be renamed    
        phone.mRuimCard.broadcastSimStateChangedIntent(
                RuimCard.INTENT_VALUE_ICC_READY, null);

        fetchRuimRecords();
        
        // TODO: add a function here
        phone.mCM.getCDMASubscription(obtainMessage(EVENT_GET_CDMA_SUBSCRIPTION_DONE));
        
    }
    
    private void fetchRuimRecords() {
        recordsRequested = true;

        Log.v(LOG_TAG, "RuimRecords:fetchRuimRecords " + recordsToLoad);
        
        // TODO: IMSI needed to get mcc and mnc    
//        phone.mCM.getIMSI(obtainMessage(EVENT_GET_IMSI_DONE));
//        recordsToLoad++;

        ((RuimFileHandler)phone.getIccFileHandler()).loadEFTransparent(EF_ICCID, 
                            obtainMessage(EVENT_GET_ICCID_DONE));
        recordsToLoad++;

        // TODO: IMSI_M(MIN)/MDN, RIL_REQUEST_DEVICE_IDENTITY
        // FIXME should examine EF[MSISDN]'s capability configuration
        // to determine which is the voice/data/fax line
//        new AdnRecordLoader(phone).loadFromEF(EF_MSISDN, EF_EXT1, 1,
//                    obtainMessage(EVENT_GET_MSISDN_DONE));
//        recordsToLoad++;

        // TODO: probably done by RIL_REQUEST_DEVICE_IDENTITY
//        phone.mSIMFileHandler.loadEFTransparent(EF_AD,  
//                        obtainMessage(EVENT_GET_AD_DONE));
//        recordsToLoad++;

        // TODO: check if if CDMA Service Table 0x6F32 should be read instead 
//        phone.mSIMFileHandler.loadEFTransparent(EF_SST,
//            obtainMessage(EVENT_GET_SST_DONE));
//        recordsToLoad++;

        // TODO: SMS handling in CDMA
        // XXX should seek instead of examining them all
//        if (false) { // XXX
//            phone.mSIMFileHandler.loadEFLinearFixedAll(EF_SMS,
//                obtainMessage(EVENT_GET_ALL_SMS_DONE));
//            recordsToLoad++;
//        }

        // TODO: check later in SMS use case
//        if (CRASH_RIL) {
//            String sms = "0107912160130310f20404d0110041007030208054832b0120ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
//            byte[] ba = IccUtils.hexStringToBytes(sms);
//
//            phone.mSIMFileHandler.updateEFLinearFixed(EF_SMS, 1, ba, null,
//                            obtainMessage(EVENT_MARK_SMS_READ_DONE, 1));
//        }
    }
    
    private void log(String s) {
        Log.d(LOG_TAG, "[RuimRecords] " + s);
    }


    @Override
    protected int getDisplayRule(String plmn) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setVoiceMessageWaiting(int line, int countWaiting) {
        // TODO Auto-generated method stub
        
    }
}

