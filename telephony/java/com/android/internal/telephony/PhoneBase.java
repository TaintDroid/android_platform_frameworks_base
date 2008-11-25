/*
 * Copyright (C) 2007 The Android Open Source Project
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

import static com.android.internal.telephony.CommandsInterface.CF_ACTION_DISABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ENABLE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_ERASURE;
import static com.android.internal.telephony.CommandsInterface.CF_ACTION_REGISTRATION;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_ALL_CONDITIONAL;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_BUSY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NOT_REACHABLE;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_NO_REPLY;
import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.ServiceState;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.test.SimulatedRadioControl;

/**
 * (<em>Not for SDK use</em>) 
 * A base implementation for the com.android.internal.telephony.Phone interface.
 * 
 * Note that implementations of Phone.java are expected to be used
 * from a single application thread. This should be the same thread that
 * originally called PhoneFactory to obtain the interface.
 *
 *  {@hide}
 *
 */

public abstract class PhoneBase implements Phone {
    private static final String LOG_TAG = "PhoneBase";
    private static final boolean LOCAL_DEBUG = false;
    
    // Key used to read and write the saved network selection value
    public static final String NETWORK_SELECTION_KEY = "network_selection_key";

 
    //***** Event Constants
    protected static final int EVENT_RADIO_AVAILABLE             = 1;
    /** Supplementary Service Notification received. */
    protected static final int EVENT_SSN                         = 2;
    protected static final int EVENT_SIM_RECORDS_LOADED          = 3;
    protected static final int EVENT_MMI_DONE                    = 4;
    protected static final int EVENT_RADIO_ON                    = 5;
    protected static final int EVENT_GET_BASEBAND_VERSION_DONE   = 6;
    protected static final int EVENT_USSD                        = 7;
    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE  = 8;
    protected static final int EVENT_GET_IMEI_DONE               = 9;
    protected static final int EVENT_GET_IMEISV_DONE             = 10;
    protected static final int EVENT_GET_SIM_STATUS_DONE         = 11;
    protected static final int EVENT_SET_CALL_FORWARD_DONE       = 12;
    protected static final int EVENT_GET_CALL_FORWARD_DONE       = 13;
    protected static final int EVENT_CALL_RING                   = 14;
    // Used to intercept the carrier selection calls so that 
    // we can save the values.
    protected static final int EVENT_SET_NETWORK_MANUAL_COMPLETE    = 15;
    protected static final int EVENT_SET_NETWORK_AUTOMATIC_COMPLETE = 16;
    protected static final int EVENT_SET_CLIR_COMPLETE              = 17;
    protected static final int EVENT_REGISTERED_TO_NETWORK          = 18;
    // Events for CDMA support
    protected static final int EVENT_GET_DEVICE_IDENTITY_DONE     = 19;
    protected static final int EVENT_RUIM_RECORDS_LOADED          = 3;    
    protected static final int EVENT_NV_READY                      = 20;        
    protected static final int EVENT_SET_ENHANCED_VP = 21;
    
    // Key used to read/write current CLIR setting
    public static final String CLIR_KEY = "clir_key";

    
    //***** Instance Variables   
    public CommandsInterface mCM;
    protected IccFileHandler mIccFileHandler;
    // TODO T: should be protected but GsmMmiCode and DataConnectionTracker still refer to it directly  
    public Handler h;
    
    /**
     * Set a system property, unless we're in unit test mode
     */
    protected void
    setSystemProperty(String property, String value) {
        if(getUnitTestMode()) {
            return;
        }
        SystemProperties.set(property, value);
    }
    

    protected final RegistrantList mPhoneStateRegistrants 
            = new RegistrantList();

    protected final RegistrantList mNewRingingConnectionRegistrants 
            = new RegistrantList();

    protected final RegistrantList mIncomingRingRegistrants 
            = new RegistrantList();
    
    protected final RegistrantList mDisconnectRegistrants 
            = new RegistrantList();

    protected final RegistrantList mServiceStateRegistrants 
            = new RegistrantList();
    
    protected final RegistrantList mMmiCompleteRegistrants 
            = new RegistrantList();

    protected final RegistrantList mMmiRegistrants 
            = new RegistrantList();

    protected final RegistrantList mUnknownConnectionRegistrants 
            = new RegistrantList();
    
    protected final RegistrantList mSuppServiceFailedRegistrants 
            = new RegistrantList();
    
    protected Looper mLooper; /* to insure registrants are in correct thread*/

    protected Context mContext;

    /** 
     * PhoneNotifier is an abstraction for all system-wide 
     * state change notification. DefaultPhoneNotifier is 
     * used here unless running we're inside a unit test.
     */
    protected PhoneNotifier mNotifier;

    protected SimulatedRadioControl mSimulatedRadioControl;

    boolean mUnitTestMode;

    /**
     * Constructs a PhoneBase in normal (non-unit test) mode.
     *
     * @param context Context object from hosting application
     * @param notifier An instance of DefaultPhoneNotifier, 
     * unless unit testing.
     */
    protected PhoneBase(PhoneNotifier notifier, Context context) {
        this(notifier, context, false);
    }

    /**
     * Constructs a PhoneBase in normal (non-unit test) mode.
     *
     * @param context Context object from hosting application
     * @param notifier An instance of DefaultPhoneNotifier, 
     * unless unit testing.
     * @param unitTestMode when true, prevents notifications 
     * of state change events
     */
    protected PhoneBase(PhoneNotifier notifier, Context context, 
                         boolean unitTestMode) {
        this.mNotifier = notifier;
        this.mContext = context;
        mLooper = Looper.myLooper();

        setUnitTestMode(unitTestMode);
    }

    // Inherited documentation suffices.
    public Context getContext() {
        return mContext;
    }

    // Inherited documentation suffices.
    public void registerForPhoneStateChanged(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mPhoneStateRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForPhoneStateChanged(Handler h) {
        mPhoneStateRegistrants.remove(h);
    }
    
    /**
     * Notify registrants of a PhoneStateChanged.
     * Subclasses of Phone probably want to replace this with a 
     * version scoped to their packages
     */
    protected void notifyCallStateChangedP() {
        AsyncResult ar = new AsyncResult(null, this, null);
        mPhoneStateRegistrants.notifyRegistrants(ar);
    }
     
    // Inherited documentation suffices.
    public void registerForUnknownConnection(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        
        mUnknownConnectionRegistrants.addUnique(h, what, obj);
    }
    
    // Inherited documentation suffices.
    public void unregisterForUnknownConnection(Handler h) {
        mUnknownConnectionRegistrants.remove(h);
    }
    
    // Inherited documentation suffices.
    public void registerForNewRingingConnection(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mNewRingingConnectionRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForNewRingingConnection(Handler h) {
        mNewRingingConnectionRegistrants.remove(h);
    }

    /**
     * Notifiy registrants of a new ringing Connection.
     * Subclasses of Phone probably want to replace this with a 
     * version scoped to their packages
     */
    protected void notifyNewRingingConnectionP(Connection cn) {    
        AsyncResult ar = new AsyncResult(null, cn, null);
        mNewRingingConnectionRegistrants.notifyRegistrants(ar);
    }

    // Inherited documentation suffices.
    public void registerForIncomingRing(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);
        
        mIncomingRingRegistrants.addUnique(h, what, obj);
    }
    
    // Inherited documentation suffices.
    public void unregisterForIncomingRing(Handler h) {
        mIncomingRingRegistrants.remove(h);
    }
    
    // Inherited documentation suffices.
    public void registerForDisconnect(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mDisconnectRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForDisconnect(Handler h) {
        mDisconnectRegistrants.remove(h);
    }

    // Inherited documentation suffices.
    public void registerForSuppServiceFailed(Handler h, int what, Object obj) {
        checkCorrectThread(h);
        
        mSuppServiceFailedRegistrants.addUnique(h, what, obj);
    }
    
    // Inherited documentation suffices.
    public void unregisterForSuppServiceFailed(Handler h) {
        mSuppServiceFailedRegistrants.remove(h);
    }
    
    // Inherited documentation suffices.
    public void registerForMmiInitiate(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mMmiRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForMmiInitiate(Handler h) {
        mMmiRegistrants.remove(h);
    }
    
    // Inherited documentation suffices.
    public void registerForMmiComplete(Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mMmiCompleteRegistrants.addUnique(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForMmiComplete(Handler h) {
        checkCorrectThread(h);

        mMmiCompleteRegistrants.remove(h);
    }

    /**
     * Method to retrieve the saved operator id from the Shared Preferences
     */
    private String getSavedNetworkSelection() {
        // open the shared preferences and search with our key. 
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sp.getString(NETWORK_SELECTION_KEY, "");
    }

    /**
     * Method to restore the previously saved operator id, or reset to
     * automatic selection, all depending upon the value in the shared
     * preferences.
     */
    public void restoreSavedNetworkSelection(Message response) {
        // retrieve the operator id
        String networkSelection = getSavedNetworkSelection();
        
        // set to auto if the id is empty, otherwise select the network.
        if (TextUtils.isEmpty(networkSelection)) {
            mCM.setNetworkSelectionModeAutomatic(response);
        } else {
            mCM.setNetworkSelectionModeManual(networkSelection, response);
        }
    }


    /**
     * Subclasses should override this. See documentation in superclass.
     */
    // Inherited documentation suffices.
    public void setUnitTestMode(boolean f) {
        mUnitTestMode = f;
    }

    // Inherited documentation suffices.
    public boolean getUnitTestMode() {
        return mUnitTestMode;
    }
    
    /**
     * To be invoked when a voice call Connection disconnects.
     *
     * Subclasses of Phone probably want to replace this with a 
     * version scoped to their packages
     */
    protected void notifyDisconnectP(Connection cn) {
        AsyncResult ar = new AsyncResult(null, cn, null);
        mDisconnectRegistrants.notifyRegistrants(ar);
    }

    // Inherited documentation suffices.
    public void registerForServiceStateChanged(
            Handler h, int what, Object obj) {
        checkCorrectThread(h);

        mServiceStateRegistrants.add(h, what, obj);
    }

    // Inherited documentation suffices.
    public void unregisterForServiceStateChanged(Handler h) {
        mServiceStateRegistrants.remove(h);
    }

    /**
     * Subclasses of Phone probably want to replace this with a 
     * version scoped to their packages
     */
    protected void notifyServiceStateChangedP(ServiceState ss) {
        AsyncResult ar = new AsyncResult(null, ss, null);
        mServiceStateRegistrants.notifyRegistrants(ar);

        mNotifier.notifyServiceState(this);
    }

    // Inherited documentation suffices.
    public SimulatedRadioControl getSimulatedRadioControl() {
        return mSimulatedRadioControl;
    }

    /**
     * Verifies the current thread is the same as the thread originally
     * used in the initialization of this instance. Throws RuntimeException
     * if not.
     *
     * @exception RuntimeException if the current thread is not
     * the thread that originally obtained this PhoneBase instance.
     */
    private void checkCorrectThread(Handler h) {
        if (h.getLooper() != mLooper) {
            throw new RuntimeException(
                "com.android.internal.telephony.Phone must be used from within one thread");
        }
    }

    /**
     * Retrieves the Handler of the Phone instance
     */ 
    protected abstract Handler getHandler();

    /**
     * Retrieves the IccFileHandler of the Phone instance
     */        
    protected abstract IccFileHandler getIccFileHandler();

    
    /**
     *  Query the status of the CDMA roaming preference
     */
    public void queryCdmaRoamingPreference(Message response) {
        mCM.queryCdmaRoamingPreference(response);
    }
    
    /**
     *  Set the status of the CDMA roaming preference
     */
    public void setCdmaRoamingPreference(int cdmaRoamingType, Message response) {
        mCM.setCdmaRoamingPreference(cdmaRoamingType, response);
    }
    
    /**
     *  Set the status of the CDMA subscription mode
     */
    public void setCdmaSubscription(int cdmaSubscriptionType, Message response) {
        mCM.setCdmaSubscription(cdmaSubscriptionType, response);
    }
    
    /**
     *  Set the preferred Network Type: Global, CDMA only or GSM/UMTS only
     */
    public void setPreferredNetworkType(int networkType, Message response) {
        mCM.setPreferredNetworkType(networkType, response);
    }

    /**
     *  Set the status of the preferred Network Type: Global, CDMA only or GSM/UMTS only
     */
    public void getPreferredNetworkType(Message response) {
        mCM.getPreferredNetworkType(response);
    }
    
    public void setTTYModeEnabled(boolean enable, Message onComplete) {
        // This function should be overridden by the class CDMAPhone. 
        // It is not implemented in the class GSMPhone.
        Log.e(LOG_TAG, "Error! This function should never be executed, because we have an " +
                "inactive CDMAPhone then.");
    }
    
    public void queryTTYModeEnabled(Message onComplete) {
        // This function should be overridden by the class CDMAPhone.
        // It is not implemented in the class GSMPhone.
        Log.e(LOG_TAG, "Error! This function should never be executed, because we have an " +
                "inactive CDMAPhone then.");
    }

    // TODO: might not be used in CDMA any longer, move this to GSMPhone
    protected  boolean isValidCommandInterfaceCFReason (int commandInterfaceCFReason) {
        switch (commandInterfaceCFReason) {
        case CF_REASON_UNCONDITIONAL:
        case CF_REASON_BUSY:
        case CF_REASON_NO_REPLY:
        case CF_REASON_NOT_REACHABLE:
        case CF_REASON_ALL:
        case CF_REASON_ALL_CONDITIONAL:
            return true;
        default:
            return false;
        }
    }

    // TODO: might not be used in CDMA any longer, move this to GSMPhone
    protected  boolean isValidCommandInterfaceCFAction (int commandInterfaceCFAction) {
        switch (commandInterfaceCFAction) {
        case CF_ACTION_DISABLE:
        case CF_ACTION_ENABLE:
        case CF_ACTION_REGISTRATION:
        case CF_ACTION_ERASURE:
            return true;
        default:
            return false;
        }
    }

    // TODO: might not be used in CDMA any longer, move this to GSMPhone
    protected  boolean isCfEnable(int action) {
        return (action == CF_ACTION_ENABLE) || (action == CF_ACTION_REGISTRATION);
    }
    
    /**
     * Make sure the network knows our preferred setting.
     */
    // TODO: might not be used in CDMA any longer, move this to GSMPhone
    protected  void syncClirSetting() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        int clirSetting = sp.getInt(CLIR_KEY, -1);
        if (clirSetting >= 0) {
            mCM.setCLIR(clirSetting, null);
        }
    }

    // TODO: might not be used in CDMA any longer, move this to GSMPhone
    public void getCallForwardingOption(int commandInterfaceCFReason, Message onComplete) {
        if (isValidCommandInterfaceCFReason(commandInterfaceCFReason)) {
            if (LOCAL_DEBUG) Log.d(LOG_TAG, "requesting call forwarding query.");
            Message resp;
            if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                resp = h.obtainMessage(EVENT_GET_CALL_FORWARD_DONE, onComplete);
            } else {
                resp = onComplete;
            }
            mCM.queryCallForwardStatus(commandInterfaceCFReason,0,null,resp);
        }
    }
    
    /**
     * Saves CLIR setting so that we can re-apply it as necessary
     * (in case the RIL resets it across reboots).
     */
    // TODO: might not be used in CDMA any longer, move this to GSMPhone
     public  void saveClirSetting(int commandInterfaceCLIRMode) {
        // open the shared preferences editor, and write the value.
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(CLIR_KEY, commandInterfaceCLIRMode);
        
        // commit and log the result.
        if (! editor.commit()) {
            Log.e(LOG_TAG, "failed to commit CLIR preference");
        }
    }
    
     // TODO: might not be used in CDMA any longer, move this to GSMPhone
    public void setCallForwardingOption(int commandInterfaceCFAction,
             int commandInterfaceCFReason,
             String dialingNumber,
             int timerSeconds,
             Message onComplete) {
         if ((isValidCommandInterfaceCFAction(commandInterfaceCFAction)) && 
                 (isValidCommandInterfaceCFReason(commandInterfaceCFReason))) {

             Message resp;
             if (commandInterfaceCFReason == CF_REASON_UNCONDITIONAL) {
                 resp = h.obtainMessage(EVENT_SET_CALL_FORWARD_DONE,
                         isCfEnable(commandInterfaceCFAction) ? 1 : 0, 0, onComplete);
             } else {
                 resp = onComplete;
             }
             mCM.setCallForward(commandInterfaceCFAction,
                     commandInterfaceCFReason,
                     CommandsInterface.SERVICE_CLASS_VOICE,
                     dialingNumber,
                     timerSeconds,
                     resp);
         }
     }
    
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        // This function should be overridden by the class CDMAPhone. 
        // It is not implemented in the class GSMPhone.
        Log.e(LOG_TAG, "Error! This function should never be executed, because we have an" +
                "inactive CDMAPhone then.");
    }

    public void getEnhancedVoicePrivacy(Message onComplete) {
        // This function should be overridden by the class CDMAPhone.
        // It is not implemented in the class GSMPhone.
        Log.e(LOG_TAG, "Error! This function should never be executed, because we have an " +
                "inactive CDMAPhone then.");
    }

}
