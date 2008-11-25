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



import java.util.ArrayList;

import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.util.Log;

import static com.android.internal.telephony.TelephonyProperties.*;

// TODO: move abstract parts to here

/**
 * {@hide}
 */
public abstract class IccRecords extends Handler implements IccConstants {
    static String LOG_TAG = "IccRecords";
    
    protected static final boolean DBG = true;    
    //***** Instance Variables

    PhoneBase phone;
    RegistrantList recordsLoadedRegistrants = new RegistrantList();

    int recordsToLoad;  // number of pending load requests

    AdnRecordCache adnCache;

    //***** Cached SIM State; cleared on channel close

    boolean recordsRequested = false; // true if we've made requests for the sim records

    String imsi;
    String iccid;
    String msisdn = null;  // My mobile number
    String msisdnTag = null;
    String voiceMailNum = null;
    String voiceMailTag = null;
    String newVoiceMailNum = null;
    String newVoiceMailTag = null;
    boolean isVoiceMailFixed = false;
    int countVoiceMessages = 0;
    boolean callForwardingEnabled;
    int mncLength = 0;   // 0 is used to indicate that the value
                         // is not initialized
    int mailboxIndex = 0; // 0 is no mailbox dailing number associated


    byte[] efMWIS = null;
    byte[] efCPHS_MWI =null;
    byte[] mEfCff = null;
    byte[] mEfCfis = null;


    String spn;
    int spnDisplayCondition;
    // Numeric network codes listed in TS 51.011 EF[SPDI]
    ArrayList<String> spdiNetworks = null;

    String pnnHomeName = null;

    //***** Constants

    // Bitmasks for SPN display rules.
    static final int SPN_RULE_SHOW_SPN  = 0x01;
    static final int SPN_RULE_SHOW_PLMN = 0x02;

    // From TS 51.011 EF[SPDI] section
    static final int TAG_SPDI_PLMN_LIST = 0x80;

    // Full Name IEI from TS 24.008
    static final int TAG_FULL_NETWORK_NAME = 0x43;

    // Short Name IEI from TS 24.008
    static final int TAG_SHORT_NETWORK_NAME = 0x45;

    // active CFF from CPHS 4.2 B.4.5
    static final int CFF_UNCONDITIONAL_ACTIVE = 0x0a;
    static final int CFF_UNCONDITIONAL_DEACTIVE = 0x05;
    static final int CFF_LINE1_MASK = 0x0f;
    static final int CFF_LINE1_RESET = 0xf0;

    //***** Event Constants
    private static final int EVENT_SET_MSISDN_DONE = 30;
    
    //***** Constructor

    public IccRecords(PhoneBase phone) {
        this.phone = phone;
    }

// TODO T: check if public can /should be avoided
    protected AdnRecordCache getAdnCache() {
        return adnCache;
    }

    protected void onRadioOffOrNotAvailable() {
        imsi = null;
        msisdn = null;
        voiceMailNum = null;
        countVoiceMessages = 0;
        mncLength = 0;
        iccid = null;
        spn = null;
        // -1 means no EF_SPN found; treat accordingly.
        spnDisplayCondition = -1;
        efMWIS = null;
        efCPHS_MWI = null; 
        spn = null;
        spdiNetworks = null;
        pnnHomeName = null;

        adnCache.reset();

        phone.setSystemProperty(PROPERTY_LINE1_VOICE_MAIL_WAITING, null);
        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_NUMERIC, null);
        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ALPHA, null);
        phone.setSystemProperty(PROPERTY_SIM_OPERATOR_ISO_COUNTRY, null);

        // recordsRequested is set to false indicating that the SIM
        // read requests made so far are not valid. This is set to
        // true only when fresh set of read requests are made.
        recordsRequested = false;
    }


    //***** Public Methods
    public void registerForRecordsLoaded(Handler h, int what, Object obj) {
        Registrant r = new Registrant(h, what, obj);
        recordsLoadedRegistrants.add(r);

        if (recordsToLoad == 0 && recordsRequested == true) {
            r.notifyRegistrant(new AsyncResult(null, null, null));
        }
    }

    /** Returns null if SIM is not yet ready */
    public String getIMSI() {
        return imsi;
    }

    public String getMsisdnNumber() {
        return msisdn;
    }

    /**
     * Set subscriber number to SIM record
     *
     * The subscriber number is stored in EF_MSISDN (TS 51.011)
     *
     * When the operation is complete, onComplete will be sent to its handler
     *
     * @param alphaTag alpha-tagging of the dailing nubmer (up to 10 characters)
     * @param number dailing nubmer (up to 20 digits)
     *        if the number starts with '+', then set to international TOA
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    public void setMsisdnNumber(String alphaTag, String number,
            Message onComplete) {

        msisdn = number;
        msisdnTag = alphaTag;

        if(DBG) log("Set MSISDN: " + msisdnTag +" " + msisdn);


        AdnRecord adn = new AdnRecord(msisdnTag, msisdn);

        new AdnRecordLoader(phone).updateEF(adn, EF_MSISDN, EF_EXT1, 1, null,
                obtainMessage(EVENT_SET_MSISDN_DONE, onComplete));
    }

    public String getMsisdnAlphaTag() {
        return msisdnTag;
    }

    public String getVoiceMailNumber() {
        return voiceMailNum;
    }

    /**
     * Return Service Provider Name stored in SIM
     * @return null if SIM is not yet ready
     */
    public String getServiceProvideName() {
        return spn;
    }

    /**
     * Set voice mail number to SIM record
     *
     * The voice mail number can be stored either in EF_MBDN (TS 51.011) or
     * EF_MAILBOX_CPHS (CPHS 4.2)
     *
     * If EF_MBDN is available, store the voice mail number to EF_MBDN
     * 
     * If EF_MAILBOX_CPHS is enabled, store the voice mail number to EF_CHPS
     *
     * So the voice mail number will be stored in both EFs if both are available
     *
     * Return error only if both EF_MBDN and EF_MAILBOX_CPHS fail.
     *
     * When the operation is complete, onComplete will be sent to its handler
     *
     * @param alphaTag alpha-tagging of the dailing nubmer (upto 10 characters)
     * @param voiceNumber dailing nubmer (upto 20 digits)
     *        if the number is start with '+', then set to international TOA
     * @param onComplete
     *        onComplete.obj will be an AsyncResult
     *        ((AsyncResult)onComplete.obj).exception == null on success
     *        ((AsyncResult)onComplete.obj).exception != null on fail
     */
    public abstract void setVoiceMailNumber(String alphaTag, String voiceNumber,
            Message onComplete);

    public String getVoiceMailAlphaTag() {
        return voiceMailTag;
    }

    /**
     * Sets the SIM voice message waiting indicator records
     * @param line GSM Subscriber Profile Number, one-based. Only '1' is supported
     * @param countWaiting The number of messages waiting, if known. Use
     *                     -1 to indicate that an unknown number of 
     *                      messages are waiting
     */
    public abstract void setVoiceMessageWaiting(int line, int countWaiting);

    /** @return  true if there are messages waiting, false otherwise. */
    public boolean getVoiceMessageWaiting() {
        return countVoiceMessages != 0;
    }

    /**
     * Returns number of voice messages waiting, if available
     * If not available (eg, on an older CPHS SIM) -1 is returned if 
     * getVoiceMessageWaiting() is true
     */
    public int getCountVoiceMessages() {
        return countVoiceMessages;
    }

    public boolean getVoiceCallForwardingFlag() {
        return callForwardingEnabled;
    }

    public abstract void setVoiceCallForwardingFlag(int line, boolean enable);


    /**
     * Called by STK Service when REFRESH is received.
     * @param fileChanged indicates whether any files changed
     * @param fileList if non-null, a list of EF files that changed
     */
    public abstract void onRefresh(boolean fileChanged, int[] fileList);


// TODO T: check if public can /should be avoided    
    public boolean getRecordsLoaded() {
        if (recordsToLoad == 0 && recordsRequested == true) {
            return true;
        } else {
            return false;
        }
    }

    //***** Overridden from Handler
    public abstract void handleMessage(Message msg);

    protected abstract void onRecordLoaded();
    
    protected abstract void onAllRecordsLoaded();
    
// TODO T: check if public can /should be avoided
    /**
     * Returns the SpnDisplayRule based on settings on the SIM and the
     * specified plmn (currently-registered PLMN).  See TS 22.101 Annex A
     * and TS 51.011 10.3.11 for details.
     *
     * If the SPN is not found on the SIM, the rule is always PLMN_ONLY.
     */
    protected abstract int getDisplayRule(String plmn);
    
    private void log(String s) {
        Log.d(LOG_TAG, " " + s);
    }
    
}
