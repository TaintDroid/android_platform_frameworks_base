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

import static com.android.internal.telephony.CommandsInterface.CF_REASON_UNCONDITIONAL;
import static com.android.internal.telephony.TelephonyProperties.PROPERTY_BASEBAND_VERSION;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Registrant;
import android.os.RegistrantList;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.telephony.CellLocation;
import android.telephony.ServiceState;
import android.util.Log;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CallStateException;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Connection;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.IccFileHandler;
import com.android.internal.telephony.IccPhoneBookInterfaceManager;
import com.android.internal.telephony.IccSmsInterfaceManager;
import com.android.internal.telephony.MmiCode;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneBase;
import com.android.internal.telephony.PhoneNotifier;
import com.android.internal.telephony.PhoneSubInfo;
import com.android.internal.telephony.gsm.GsmMmiCode;
import com.android.internal.telephony.gsm.PdpConnection;
import com.android.internal.telephony.RILConstants;

import android.telephony.PhoneNumberUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@hide}
 */
public class CDMAPhone extends PhoneBase {
    static final String LOG_TAG = "CDMA";
    private static final boolean LOCAL_DEBUG = false;
     
    //***** Instance Variables
    CdmaCallTracker mCT;
    SMSDispatcher mSMS;
    CdmaServiceStateTracker mSST;
//    DataConnectionTracker mDataConnection; //TODO
    RuimRecords mRuimRecords;
    RuimCard mRuimCard;
    //MyHandler h;
    RuimPhoneBookInterfaceManager mRuimPhoneBookInterfaceManager;
    RuimSmsInterfaceManager mRuimSmsInterfaceManager;
    PhoneSubInfo mSubInfo;

    protected RegistrantList mNvLoadedRegistrants = new RegistrantList();
    private String mEsn;
    private String mMeid;

    Registrant mPostDialHandler;

     
    //***** Constructors
    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier) {
        this(context,ci,notifier, false);
        //TODO: to be checked if really needed
    }
    
    public CDMAPhone(Context context, CommandsInterface ci, PhoneNotifier notifier, boolean unitTestMode) {
        super(notifier, context, unitTestMode);
        //TODO: to be checked if really needed
        
        h = new MyHandler();
        mCM = ci;
 
        mCM.setPhoneType(RILConstants.CDMA_PHONE);       
        mCT = new CdmaCallTracker(this);   
        mSST = new CdmaServiceStateTracker (this);
        mSMS = new SMSDispatcher(this);
        mIccFileHandler = new RuimFileHandler(this);
        mRuimRecords = new RuimRecords(this);
//      mDataConnection = new DataConnectionTracker (this); //TODO
        mRuimCard = new RuimCard(this);
        mRuimPhoneBookInterfaceManager = new RuimPhoneBookInterfaceManager(this);
        mRuimSmsInterfaceManager = new RuimSmsInterfaceManager(this);
        mSubInfo = new PhoneSubInfo(this);

        mCM.registerForAvailable(h, EVENT_RADIO_AVAILABLE, null);
        mRuimRecords.registerForRecordsLoaded(h, EVENT_RUIM_RECORDS_LOADED, null);
        mCM.registerForOffOrNotAvailable(h, EVENT_RADIO_OFF_OR_NOT_AVAILABLE, 
                null);
        mCM.registerForOn(h, EVENT_RADIO_ON, null);
        mCM.setOnSuppServiceNotification(h, EVENT_SSN, null);
        mCM.setOnCallRing(h, EVENT_CALL_RING, null);
        mSST.registerForNetworkAttach(h, EVENT_REGISTERED_TO_NETWORK, null);
        mCM.registerForNVReady(h, EVENT_NV_READY, null);
    }
    
    
    //***** Overridden from Phone
    public ServiceState getServiceState() {
        return mSST.ss;
    }   
    
    public Phone.State 
    getState() {
        return mCT.state;
    }
    
    public String
    getPhoneName() {
        return "CDMA";
    }
    
    public boolean canTransfer() {
        // TODO: to be implemented
        //return mCT.canTransfer();
        return false;
    }
    
    public CdmaCall 
    getRingingCall() {
        return mCT.ringingCall;
    }
    
    public void setMute(boolean muted) {
        mCT.setMute(muted);
    }
    public boolean getMute() {
        return mCT.getMute();
    }
    
    public void conference() { //throws CallStateException 
        //TODO: ...
        
    }
    
    public void enableEnhancedVoicePrivacy(boolean enable, Message onComplete) {
        this.mCM.setPreferredVoicePrivacy(enable, onComplete);
    }
    
    public void getEnhancedVoicePrivacy(Message onComplete) {
        this.mCM.getPreferredVoicePrivacy(onComplete);
    }

    public void clearDisconnected() {
        mCT.clearDisconnected();
    }
    
    public DataActivityState getDataActivityState() {
        DataActivityState ret = DataActivityState.NONE;

        if (mSST.getCurrentCdmaDataConnectionState() != ServiceState.RADIO_TECHNOLOGY_UNKNOWN) {

            // TODO This "switch" has to be implemented, when the CDMAPhone is able to generate a cdma.DataConnectionTracker
            // Until this will happen, we return DataActivityState.DATAIN!!!

            ret = DataActivityState.DATAIN; // Remove this, when "switch" is implemented!

            /*switch (mDataConnection.activity) {

            case DATAIN:
                ret = DataActivityState.DATAIN;
            break;

            case DATAOUT:
                ret = DataActivityState.DATAOUT;
            break;

            case DATAINANDOUT:
                ret = DataActivityState.DATAINANDOUT;
            break;
            }*/
        }

        return ret;
    }
    
    /*package*/ void
    notifySignalStrength() {
        mNotifier.notifySignalStrength(this);
    }

    public Connection
    dial (String dialString) throws CallStateException {
        // Need to make sure dialString gets parsed properly
        String newDialString = PhoneNumberUtils.stripSeparators(dialString);

        return mCT.dial(newDialString);
    }
    
    public int getSignalStrengthASU() {
        return mSST.rssi == 99 ? -1 : mSST.rssi;
    }

    public boolean
    getMessageWaitingIndicator() {
        //TODO: ...
//      throw new RuntimeException(); 
        return false; 
    }
    public List<? extends MmiCode>
    getPendingMmiCodes() {
        ArrayList <GsmMmiCode> mPendingMMIs = new ArrayList<GsmMmiCode>();
        Log.e(LOG_TAG, "method getPendingMmiCodes is NOT supported in CDMA!");
        return mPendingMMIs;
    }
    public void registerForSuppServiceNotification(
            Handler h, int what, Object obj) {
        //TODO: ....
    }
    
    public CdmaCall getBackgroundCall() {
        return mCT.backgroundCall;
    }
    
    public String getGateway(String apnType) {
        //TODO: ....
//      throw new RuntimeException();  
        return null; 
    }
    
    public boolean handleInCallMmiCommands(String dialString) {
        Log.e(LOG_TAG, "method handleInCallMmiCommands is NOT supported in CDMA!");
        return false;
    }
  
    public int enableApnType(String type) {
        //TODO: ....
        return 42;
    }
    public int disableApnType(String type) {
        //TODO: to be implemented
        return 42;
    }
    
    public String getActiveApn() {
        //TODO: ...
//      throw new RuntimeException();  
        return null; 
    }
    
    public void 
    setNetworkSelectionModeAutomatic(Message response) {
        //TODO: ...
    }
    
    public void unregisterForSuppServiceNotification(Handler h) {
        //TODO: ...
    }
    
    public void 
    acceptCall() throws CallStateException {
        mCT.acceptCall();
    }

    public void 
    rejectCall() throws CallStateException {
        mCT.rejectCall();
    }

    public void
    switchHoldingAndActive() throws CallStateException {
        mCT.switchWaitingOrHoldingAndActive();
    }
    
    public String getLine1Number() {
        return mRuimRecords.getMdnNumber();
    }

    public void stopDtmf() {
        //TODO: ...
    }

    // TODO: might not be used any longer in CDMA
    public void getCallWaiting(Message onComplete) {
        mCM.queryCallWaiting(CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }
    
    public void
    setRadioPower(boolean power) {
        mSST.setRadioPower(power);
    }
    
    public String getEsn() {
        return mEsn;
    }
    
    public String getMeid() {
        return mMeid;
    }

    
    public String getDeviceId() {
        Log.d(LOG_TAG, "getDeviceId(): return 0");
        return "0";
    }
    
    public String getDeviceSvn() {
        Log.d(LOG_TAG, "getDeviceSvn(): return 0");
        return "0";
    }
    
    public String getSubscriberId() {
        //TODO: ...
//      throw new RuntimeException();  
        return null; 
    }
    
    public boolean canConference() {
        // TODO: to be implemented
        return false;
    }
    
    public String getInterfaceName(String apnType) {
        //TODO: ....
//      throw new RuntimeException();  
        return null; 
    }
    
    public CellLocation getCellLocation() {
        return mSST.cellLoc;
    }
    
    public boolean disableDataConnectivity() {
        //TODO:
//      throw new RuntimeException();  
        return true; 
    }
    public void setBandMode(int bandMode, Message response) {
        //TODO: ...
    }
    
    public CdmaCall getForegroundCall() {
        return mCT.foregroundCall;
    }
    
    public void 
    selectNetworkManually(com.android.internal.telephony.gsm.NetworkInfo network,
            Message response) {
        //TODO: ...
    }
    
    public void setOnPostDialCharacter(Handler h, int what, Object obj) {
        //TODO: ...
    }
    
    public boolean handlePinMmi(String dialString) {
        Log.e(LOG_TAG, "method handlePinMmi is NOT supported in CDMA!");
        return false; 
    }
    
    public boolean isDataConnectivityPossible() {
        // TODO: Currently checks if any GPRS connection is active. Should it only
        // check for "default"?

        // TODO This function has to be implemented, when the CDMAPhone is able to generate a cdma.DataConnectionTracker
        // Until this will happen, we return TRUE!!!

        return true;
        /*boolean noData = mDataConnection.getDataEnabled() &&
        getDataConnectionState() == DataState.DISCONNECTED;
        return !noData && getSimCard().getState() == SimCard.State.READY &&
        getServiceState().getState() == ServiceState.STATE_IN_SERVICE &&
        (mDataConnection.getDataOnRoamingEnabled() || !getServiceState().getRoaming());*/
    }
    
    public void setLine1Number(String alphaTag, String number, Message onComplete) {
        //TODO: ...
    }
    
    public String[] getDnsServers(String apnType) {
        //TODO: ....
//      throw new RuntimeException();  
        return null; 
    }
    
    public IccCard getIccCard() {
        return mRuimCard;
    }
    
    public String getIccSerialNumber() {
        return mRuimRecords.iccid;
    }
    
    public void queryAvailableBandMode(Message response) {
        //TODO: ....
    }
    
    // TODO: might not be used any longer in CDMA
    public void setCallWaiting(boolean enable, Message onComplete) {
        mCM.setCallWaiting(enable, CommandsInterface.SERVICE_CLASS_VOICE, onComplete);
    }
    
    public void updateServiceLocation(Message response) {
        mSST.getLacAndCid(response);
    }

    public void setDataRoamingEnabled(boolean enable) {
        //TODO: ....
    }
    
    public String getIpAddress(String apnType) {
        //TODO: ....
//      throw new RuntimeException(); 
        return null;  
    }
    
    public void
    getNeighboringCids(Message response) {
        //TODO: ....
//      throw new RuntimeException();  
    }
    
    public DataState getDataConnectionState() {
        DataState ret = DataState.DISCONNECTED;

        if ((SystemProperties.get("adb.connected", "").length() > 0)
                && (SystemProperties.get("android.net.use-adb-networking", "")
                        .length() > 0)) {
            // We're connected to an ADB host and we have USB networking
            // turned on. No matter what the radio state is,
            // we report data connected

            ret = DataState.CONNECTED;
        } else if (mSST.getCurrentCdmaDataConnectionState() == ServiceState.RADIO_TECHNOLOGY_UNKNOWN) {
            // If we're out of service, open TCP sockets may still work
            // but no data will flow
            ret = DataState.DISCONNECTED;
        } else { /* mSST.getCurrentCdmaDataConnectionState() is ServiceState.RADIO_TECHNOLOGY_1xRTT or EVDO */


            // TODO This "switch" has to be implemented, when the CDMAPhone is able to generate a cdma.DataConnectionTracker
            // Until this will happen, we return DataState.CONNECTED!!!

            ret = DataState.CONNECTED; //TODO Remove this, when "switch" iis implemented

            /* switch (mDataConnection.state) {
            case FAILED:
            case IDLE:
                ret = DataState.DISCONNECTED;
            break;

            case CONNECTED:
                if ( mCT.state != Phone.State.IDLE
                        && !mSST.isConcurrentVoiceAndData())
                    ret = DataState.SUSPENDED;
                else
                    ret = DataState.CONNECTED;
            break;

            case INITING:
            case CONNECTING:
            case SCANNING:
                ret = DataState.CONNECTING;
            break;
            }*/
        }

        return ret;
    }

    public void sendUssdResponse(String ussdMessge) {
        //TODO: ...
    }
    public void sendDtmf(char c) {
        //TODO: ....
    }
    
    public void startDtmf(char c) {
        //TODO: ....
    }
    
    public void 
    getAvailableNetworks(Message response) {
        //TODO: ....
    }
    
    public String[] getActiveApnTypes() {
        //TODO: .....
//      throw new RuntimeException();  
        return null; 
    }
    
    public void setOutgoingCallerIdDisplay(int commandInterfaceCLIRMode, Message onComplete) {
        // TODO: might not be used any longer in CDMA
//        mCM.setCLIR(commandInterfaceCLIRMode, h.obtainMessage(EVENT_SET_CLIR_COMPLETE, commandInterfaceCLIRMode, 0, onComplete));
    }
    
    public void enableLocationUpdates() {
        mSST.enableLocationUpdates();
    }
    
    public void getPdpContextList(Message response) {
        //TODO: to be implemented
    }
    
    public boolean getDataRoamingEnabled() {
        //TODO: ....
        return false; //mDataConnection.getDataOnRoamingEnabled();
    }

    
    public List<PdpConnection> getCurrentPdpList () {
        //TODO: to be implemented and import pdpConcecntion from
        // GSM to be removed/replaced
        return null;
    }

    public void setVoiceMailNumber(String alphaTag,
                                   String voiceMailNumber,
                                   Message onComplete) {
        //mSIMRecords.setVoiceMailNumber(alphaTag, voiceMailNumber, onComplete);
        //TODO: Where do we have to store this value has to be clarified with QC
    }
    
    public String getVoiceMailNumber() {
        //TODO: Where can we get this value has to be clarified with QC
        //return mSIMRecords.getVoiceMailNumber();
//      throw new RuntimeException(); 
        return "12345";
    }
    
    public String getVoiceMailAlphaTag() {
        // TODO: Where can we get this value has to be clarified with QC.
        String ret = "";//TODO: Remove = "", if we know where to get this value.

        //ret = mSIMRecords.getVoiceMailAlphaTag();

        if (ret == null || ret.length() == 0) {
            return mContext.getText(
                com.android.internal.R.string.defaultVoiceMailAlphaTag).toString();
        }

        return ret; 
    }


    public boolean enableDataConnectivity() {
        //TODO: to be implemented
        return false;
    }

    public void disableLocationUpdates() {
        mSST.disableLocationUpdates();
    }

    public boolean
    getSimRecordsLoaded() {
        // TODO: this method is expected to be renamed
        //       and implemented in PhoneBase!!!
        return mRuimRecords.getRecordsLoaded();
    }
    
    public void invokeOemRilRequestRaw(byte[] data, Message response) {
        //TODO: .....
    }
    public void invokeOemRilRequestStrings(String[] strings, Message response) {
        // TODO: to be implemented
    }

    // TODO: might not be used any longer in CDMA
    public void 
    getOutgoingCallerIdDisplay(Message onComplete) {
        // TODO: might not be used any longer in CDMA
//        mCM.getCLIR(onComplete);
    }
    
    public boolean
    getCallForwardingIndicator() {
        // TODO: to be implemented
        return false;
    }

    public void explicitCallTransfer() {
        //TODO: to be implemented like GSM
    }    
    
    public String getLine1AlphaTag() {
        // TODO: to be implemented
        String ret = "to be implemented";
        return ret;
    }
     

    /**
     * Notify any interested party of a Phone state change.
     */
    /*package*/ void notifyPhoneStateChanged() {
        mNotifier.notifyPhoneState(this);
    }
    
    /**
     * Notifies registrants (ie, activities in the Phone app) about
     * changes to call state (including Phone and Connection changes).
     */
    /*package*/ void
    notifyCallStateChanged() {
        /* we'd love it if this was package-scoped*/
        super.notifyCallStateChangedP();
    }
    
     void notifyServiceStateChanged(ServiceState ss) {
         super.notifyServiceStateChangedP(ss);
     }

     void notifyLocationChanged() {
         mNotifier.notifyCellLocation(this);
     }
    
     void notifyDataConnection(String reason) {
        mNotifier.notifyDataConnection(this, reason);
    }
   
    /*package*/ void
    notifyNewRingingConnection(Connection c) {
        /* we'd love it if this was package-scoped*/
        super.notifyNewRingingConnectionP(c);
    }

    /**
     * Notifiy registrants of a RING event.
     */
    void notifyIncomingRing() {    
        AsyncResult ar = new AsyncResult(null, this, null);
        mIncomingRingRegistrants.notifyRegistrants(ar);
    }
    
    /*package*/ void
    notifyDisconnect(Connection cn) {
        mDisconnectRegistrants.notifyResult(cn);
    }

    void notifyUnknownConnection() {
        mUnknownConnectionRegistrants.notifyResult(this);
    }
     
    //***** Inner Classes
    class MyHandler extends Handler {
        MyHandler() {
        }

        MyHandler(Looper l) {
            super(l);
        }

        public void
        handleMessage(Message msg) {
            AsyncResult ar;
            Message     onComplete;

            switch(msg.what) {
                case EVENT_RADIO_AVAILABLE: {
                    Log.d(LOG_TAG, "Event EVENT_RADIO_AVAILABLE Received"); //TODO Remove
                    mCM.getBasebandVersion(obtainMessage(EVENT_GET_BASEBAND_VERSION_DONE));

                    mCM.getDeviceIdentity(obtainMessage(EVENT_GET_DEVICE_IDENTITY_DONE));
                }
                break;

                case EVENT_GET_BASEBAND_VERSION_DONE:{
                    Log.d(LOG_TAG, "Event EVENT_GET_BASEBAND_VERSION_DONE Received"); //TODO Remove
                    ar = (AsyncResult)msg.obj;

                    if (ar.exception != null) {
                        break;
                    }

                    if (LOCAL_DEBUG) Log.d(LOG_TAG, "Baseband version: " + ar.result);
                    setSystemProperty(PROPERTY_BASEBAND_VERSION, (String)ar.result);
                }
                break;
                
                case EVENT_GET_DEVICE_IDENTITY_DONE:{
                    Log.d(LOG_TAG, "Event EVENT_GET_DEVICE_IDENTITY_DONE Received"); //TODO Remove    
                    ar = (AsyncResult)msg.obj;

                    if (ar.exception != null) {
                        break;
                    }
                    String[] localTemp = (String[])ar.result;                  
                    mEsn  =  localTemp[2];
                    mMeid =  localTemp[3];
                    Log.d(LOG_TAG, "ESN: " + mEsn); //TODO Remove  
                    Log.d(LOG_TAG, "MEID: " + mMeid); //TODO Remove  
                }
                break;

                case EVENT_RUIM_RECORDS_LOADED:{
                    Log.d(LOG_TAG, "Event EVENT_RUIM_RECORDS_LOADED Received"); //TODO Remove   
                    //TODO 
                }
                break;

                case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:{
                    Log.d(LOG_TAG, "Event EVENT_RADIO_OFF_OR_NOT_AVAILABLE Received"); //TODO Remove   
                    //TODO 
                }
                break;

                case EVENT_RADIO_ON:{
                    Log.d(LOG_TAG, "Event EVENT_RADIO_ON Received"); //TODO Remove    
                }
                break;

                case EVENT_SSN:{
                    Log.d(LOG_TAG, "Event EVENT_SSN Received"); //TODO Remove    
                }
                break;

                case EVENT_CALL_RING:{
                    Log.d(LOG_TAG, "Event EVENT_CALL_RING Received"); //TODO Remove    
                } 
                break;

                case EVENT_REGISTERED_TO_NETWORK:{
                    Log.d(LOG_TAG, "Event EVENT_REGISTERED_TO_NETWORK Received"); //TODO Remove    
                    // TODO: might not be used any longer in CDMA
                    // syncClirSetting();
                }                 
                break;

                case EVENT_NV_READY:{
                    Log.d(LOG_TAG, "Event EVENT_NV_READY Received"); //TODO Remove    
                    //Get the records from NV
                    //TODO
                    //Inform the Service State Tracker
                    mNvLoadedRegistrants.notifyRegistrants();
                }                 
                break;

            // TODO: might not be used any longer in CDMA
//            case EVENT_GET_CALL_FORWARD_DONE:
//                ar = (AsyncResult)msg.obj;
//                if (ar.exception == null) {
//                    handleCfuQueryResult((CallForwardInfo[])ar.result);
//                }
//                onComplete = (Message) ar.userObj;
//                if (onComplete != null) {
//                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
//                    onComplete.sendToTarget();
//                }                 
//                break;

            // TODO: might not be used any longer in CDMA
//            case EVENT_SET_CALL_FORWARD_DONE:
//                ar = (AsyncResult)msg.obj;
//
//                // TODO: Normally, we don't have to set this flag at the RUIM card too. But check it again before deleting this code.
//                /*if (ar.exception == null) {
//                    mSIMRecords.setVoiceCallForwardingFlag(1, msg.arg1 == 1);
//                }*/
//
//                onComplete = (Message) ar.userObj;
//                if (onComplete != null) {
//                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
//                    onComplete.sendToTarget();
//                }
//                break;

            // TODO: might not be used any longer in CDMA
//            case EVENT_SET_CLIR_COMPLETE:
//                ar = (AsyncResult)msg.obj;
//                if (ar.exception == null) {
//                    saveClirSetting(msg.arg1);
//                }
//                onComplete = (Message) ar.userObj;
//                if (onComplete != null) {
//                    AsyncResult.forMessage(onComplete, ar.result, ar.exception);
//                    onComplete.sendToTarget();
//                }
//                break;
                
                default:{
                    throw new RuntimeException("unexpected event not handled");
                }
            }
            }    
    }
    
    private void handleCfuQueryResult(CallForwardInfo[] infos) {

        // TODO: Normally, we don't need to set this flag at the RUIM card too. But this has to be checked.
        // Remove this function, if we don't need it in CDMA.

        /*if (infos == null || infos.length == 0) {
            // Assume the default is not active
            // Set unconditional CFF in SIM to false
            mSIMRecords.setVoiceCallForwardingFlag(1, false);
        } else {
            for (int i = 0, s = infos.length; i < s; i++) {
                if ((infos[i].serviceClass & SERVICE_CLASS_VOICE) != 0) {
                    mSIMRecords.setVoiceCallForwardingFlag(1, (infos[i].status == 1));
                    // should only have the one
                    break;
                }
            }
        }*/
    }

     /**
      * Retrieves the PhoneSubInfo of the CDMAPhone
      */
     public PhoneSubInfo getPhoneSubInfo(){
        return mSubInfo;
     }

     /**
      * Retrieves the IccSmsInterfaceManager of the CDMAPhone
      */
     public IccSmsInterfaceManager getIccSmsInterfaceManager(){
         //TODO
         return null;
     }

     /**
      * Retrieves the IccPhoneBookInterfaceManager of the CDMAPhone
      */
     public IccPhoneBookInterfaceManager getIccPhoneBookInterfaceManager(){
         return mRuimPhoneBookInterfaceManager;
     }

    public void registerForNvLoaded(Handler h, int what, Object obj) {
        Registrant r = new Registrant (h, what, obj);
        mNvLoadedRegistrants.add(r);
    }
     
     // override for allowing access from other classes of this package
     /**
      * {@inheritDoc}
      */
     protected final void
     setSystemProperty(String property, String value) {
         super.setSystemProperty(property, value);
     }     
     
     /**
      * {@inheritDoc}
      */    
     protected Handler getHandler(){
         return h;
     }
     
     /**
      * {@inheritDoc}
      */  
     protected IccFileHandler getIccFileHandler(){
         return this.mIccFileHandler;    
     }
       
     /**
      * Set the TTY mode of the CDMAPhone
      */
     public void setTTYModeEnabled(boolean enable, Message onComplete) {
         this.mCM.setTTYModeEnabled(enable, onComplete);
}

     /**
      * Queries the TTY mode of the CDMAPhone
      */
     public void queryTTYModeEnabled(Message onComplete) {
         this.mCM.queryTTYModeEnabled(onComplete);
     }
}
