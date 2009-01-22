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

import android.os.AsyncResult;
import android.os.Handler;
import android.os.INetStatService;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

/**
 * {@hide}
 *
 */
public abstract class DataConnectionTracker extends Handler {
    private static final boolean DBG = true;

    /**
     * IDLE: ready to start data connection setup, default state
     * INITING: state of issued setupDefaultPDP() but not finish yet
     * CONNECTING: state of issued startPppd() but not finish yet
     * SCANNING: data connection fails with one apn but other apns are available
     *           ready to start data connection on other apns (before INITING)
     * CONNECTED: IP connection is setup
     * FAILED: data connection fail for all apns settings
     *
     * getDataConnectionState() maps State to DataState
     *      FAILED or IDLE : DISCONNECTED
     *      INITING or CONNECTING or SCANNING: CONNECTING
     *      CONNECTED : CONNECTED
     */
    public enum State {
        IDLE,
        INITING,
        CONNECTING,
        SCANNING,
        CONNECTED,
        FAILED
    }

    public enum Activity {
        NONE,
        DATAIN,
        DATAOUT,
        DATAINANDOUT
    }

    //***** Event Codes
    protected static final int EVENT_DATA_SETUP_COMPLETE = 1;
    protected static final int EVENT_RADIO_AVAILABLE = 3;
    protected static final int EVENT_RECORDS_LOADED = 4;
    protected static final int EVENT_TRY_SETUP_DATA = 5;
    protected static final int EVENT_DATA_STATE_CHANGED = 6;
    protected static final int EVENT_POLL_PDP = 7;
    protected static final int EVENT_GET_PDP_LIST_COMPLETE = 11;
    protected static final int EVENT_RADIO_OFF_OR_NOT_AVAILABLE = 12;
    protected static final int EVENT_VOICE_CALL_STARTED = 14;
    protected static final int EVENT_VOICE_CALL_ENDED = 15;
    protected static final int EVENT_GPRS_DETACHED = 19;
    protected static final int EVENT_LINK_STATE_CHANGED = 20;
    protected static final int EVENT_ROAMING_ON = 21;
    protected static final int EVENT_ROAMING_OFF = 22;
    protected static final int EVENT_ENABLE_NEW_APN = 23;
    protected static final int EVENT_RESTORE_DEFAULT_APN = 24;
    protected static final int EVENT_DISCONNECT_DONE = 25;
    protected static final int EVENT_GPRS_ATTACHED = 26;
    protected static final int EVENT_START_NETSTAT_POLL = 27;
    protected static final int EVENT_START_RECOVERY = 28;
    protected static final int EVENT_CDMA_DATA_DETACHED = 29;
    protected static final int EVENT_NV_READY = 30;

    //***** Constants
    protected static final int RECONNECT_DELAY_INITIAL_MILLIS = 5 * 1000;

    /** Slow poll when attempting connection recovery. */
    protected static final int POLL_NETSTAT_SLOW_MILLIS = 5000;
    /** Default ping deadline, in seconds. */
    protected final int DEFAULT_PING_DEADLINE = 5;
    /** Default max failure count before attempting to network re-registration. */
    protected final int DEFAULT_MAX_PDP_RESET_FAIL = 3;

    /**
     * After detecting a potential connection problem, this is the max number
     * of subsequent polls before attempting a radio reset.  At this point,
     * poll interval is 5 seconds (POLL_NETSTAT_SLOW_MILLIS), so set this to
     * poll for about 2 more minutes.
     */
    protected static final int NO_RECV_POLL_LIMIT = 24;

    // 1 sec. default polling interval when screen is on.
    protected static final int POLL_NETSTAT_MILLIS = 1000;
    // 10 min. default polling interval when screen is off.
    protected static final int POLL_NETSTAT_SCREEN_OFF_MILLIS = 1000*60*10;
    // 2 min for round trip time
    protected static final int POLL_LONGEST_RTT = 120 * 1000;
    // 10 for packets without ack
    protected static final int NUMBER_SENT_PACKETS_OF_HANG = 10;
    // how long to wait before switching back to default APN
    protected static final int RESTORE_DEFAULT_APN_DELAY = 1 * 60 * 1000;
    // system property that can override the above value
    protected static final String APN_RESTORE_DELAY_PROP_NAME = "android.telephony.apn-restore";
    // represents an invalid IP address
    protected static final String NULL_IP = "0.0.0.0";


    // member variables
    protected PhoneBase phone;
    protected Activity activity = Activity.NONE;
    protected State state = State.IDLE;
    protected Handler mDataConnectionTracker = null;


    protected INetStatService netstat;
    protected int txPkts, rxPkts, sentSinceLastRecv, netStatPollPeriod;
    protected int mNoRecvPollCount = 0;
    protected boolean netStatPollEnabled = false;

    /**
     * Default constructor
     */
    protected DataConnectionTracker(PhoneBase phone) {
        super();
        this.phone = phone;
    }

    public Activity getActivity() {
        return activity;
    }

    public State getState() {
        return state;
    }

    //The data roaming setting is now located in the shared preferences.
    //  See if the requested preference value is the same as that stored in
    //  the shared values.  If it is not, then update it.
    public void setDataOnRoamingEnabled(boolean enabled) {
        if (getDataOnRoamingEnabled() != enabled) {
            Settings.System.putInt(phone.getContext().getContentResolver(),
                    Settings.System.DATA_ROAMING, enabled ? 1 : 0);
        }
        Message roamingMsg = phone.getServiceState().getRoaming() ?
            obtainMessage(EVENT_ROAMING_ON) : obtainMessage(EVENT_ROAMING_OFF);
        sendMessage(roamingMsg);
    }

    //Retrieve the data roaming setting from the shared preferences.
    public boolean getDataOnRoamingEnabled() {
        try {
            return Settings.System.getInt(phone.getContext().getContentResolver(),
                    Settings.System.DATA_ROAMING) > 0;
        } catch (SettingNotFoundException snfe) {
            return false;
        }
    }

    // abstract handler methods
    protected abstract void onTrySetupData();
    protected abstract void onRoamingOff();
    protected abstract void onRoamingOn();
    protected abstract void onRadioAvailable();
    protected abstract void onRadioOffOrNotAvailable();
    protected abstract void onDataSetupComplete(AsyncResult ar);
    protected abstract void onDisconnectDone();
    protected abstract void onVoiceCallStarted();
    protected abstract void onVoiceCallEnded();

  //***** Overridden from Handler
    public void handleMessage (Message msg) {
        switch (msg.what) {

            case EVENT_TRY_SETUP_DATA:
                onTrySetupData();
                break;

            case EVENT_ROAMING_OFF:
                onRoamingOff();
                break;

            case EVENT_ROAMING_ON:
                onRoamingOn();
                break;

            case EVENT_RADIO_AVAILABLE:
                onRadioAvailable();
                break;

            case EVENT_RADIO_OFF_OR_NOT_AVAILABLE:
                onRadioOffOrNotAvailable();
                break;

            case EVENT_DATA_SETUP_COMPLETE:
                onDataSetupComplete((AsyncResult) msg.obj);
                break;

            case EVENT_DISCONNECT_DONE:
                onDisconnectDone();
                break;

            case EVENT_VOICE_CALL_STARTED:
                onVoiceCallStarted();
                break;

            case EVENT_VOICE_CALL_ENDED:
                onVoiceCallEnded();
                break;

            default:
                Log.e("DATA", "Unidentified event = " + msg.what);
                break;
        }
    }

    /**
     * Report the current state of data connectivity (enabled or disabled)
     * @return {@code false} if data connectivity has been explicitly disabled,
     * {@code true} otherwise.
     */
    public abstract boolean getDataEnabled();

    /**
     * Prevent mobile data connections from being established,
     * or once again allow mobile data connections. If the state
     * toggles, then either tear down or set up data, as
     * appropriate to match the new state.
     * @param enable indicates whether to enable ({@code true}) or disable ({@code false}) data
     * @return {@code true} if the operation succeeded
     */
    public abstract boolean setDataEnabled(boolean enable);

    protected Runnable mPollNetStat = new Runnable() {

        public void run() {
            int sent, received;
            int preTxPkts = -1, preRxPkts = -1;

            Activity newActivity;

            preTxPkts = txPkts;
            preRxPkts = rxPkts;

            // check if netstat is still valid to avoid NullPointerException after NTC
            if (netstat != null) {
                try {
                    txPkts = netstat.getTxPackets();
                    rxPkts = netstat.getRxPackets();
                } catch (RemoteException e) {
                    txPkts = 0;
                    rxPkts = 0;
                }

                //Log.d(LOG_TAG, "rx " + String.valueOf(rxPkts) + " tx " + String.valueOf(txPkts));

                if (netStatPollEnabled && (preTxPkts > 0 || preRxPkts > 0)) {
                    sent = txPkts - preTxPkts;
                    received = rxPkts - preRxPkts;

                    if ( sent > 0 && received > 0 ) {
                        sentSinceLastRecv = 0;
                        newActivity = Activity.DATAINANDOUT;
                    } else if (sent > 0 && received == 0) {
                        if (phone.getState()  == Phone.State.IDLE) {
                            sentSinceLastRecv += sent;
                        } else {
                            sentSinceLastRecv = 0;
                        }
                        newActivity = Activity.DATAOUT;
                    } else if (sent == 0 && received > 0) {
                        sentSinceLastRecv = 0;
                        newActivity = Activity.DATAIN;
                    } else if (sent == 0 && received == 0) {
                        newActivity = Activity.NONE;
                    } else {
                        sentSinceLastRecv = 0;
                        newActivity = Activity.NONE;
                    }

                    if (activity != newActivity) {
                        activity = newActivity;
                        phone.notifyDataActivity();
                    }
                }

                if (sentSinceLastRecv >= NUMBER_SENT_PACKETS_OF_HANG) {
                    // we already have NUMBER_SENT_PACKETS sent without ack
                    if (mNoRecvPollCount < NO_RECV_POLL_LIMIT) {
                        mNoRecvPollCount++;
                        // Slow down the poll interval to let things happen
                        netStatPollPeriod = POLL_NETSTAT_SLOW_MILLIS;
                    } else {
                        if (DBG) log("Sent " + String.valueOf(sentSinceLastRecv) +
                                            " pkts since last received");
                        // We've exceeded the threshold.  Restart the radio.
                        netStatPollEnabled = false;
                        stopNetStatPoll();
                        restartRadio();
                    }
                } else {
                    mNoRecvPollCount = 0;
                    netStatPollPeriod = POLL_NETSTAT_MILLIS;
                }

                if (netStatPollEnabled) {
                    mDataConnectionTracker.postDelayed(this, netStatPollPeriod);
                }
            }
        }
    };

    protected abstract void startNetStatPoll();

    protected abstract void stopNetStatPoll();

    protected abstract void restartRadio();

    protected abstract void log(String s);
}
