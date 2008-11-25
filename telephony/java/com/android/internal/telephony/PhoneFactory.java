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
import java.util.List;
import java.io.IOException;
import java.net.InetSocketAddress;

import java.util.Collections;

import android.util.Log;
import com.android.internal.telephony.gsm.GSMPhone;
import com.android.internal.telephony.cdma.CDMAPhone;
import com.android.internal.telephony.RILConstants;
import com.android.internal.telephony.test.SimulatedCommands;

import android.os.Looper;
import android.os.SystemProperties;
import android.content.Context;
import android.content.Intent;
import android.net.LocalServerSocket;
import android.app.ActivityManagerNative;
import android.provider.Settings;
import android.telephony.cdma.TtyIntent;

/**
 * {@hide}
 */
public class PhoneFactory {
    static final String LOG_TAG = "PhoneFactory";
    static final int SOCKET_OPEN_RETRY_MILLIS = 2 * 1000;
    static final int SOCKET_OPEN_MAX_RETRY = 3;
    //***** Class Variables 

    static private Phone sPhone = null;
    static private CommandsInterface sCommandsInterface = null;

    static private boolean sMadeDefaults = false;
    static private PhoneNotifier sPhoneNotifier;
    static private Looper sLooper;

/*    public static final int NETWORK_MODE_GLOBAL = 0;
    public static final int NETWORK_MODE_CDMA = 1;
    public static final int NETWORK_MODE_GSM_UMTS = 2;

    public static final int SUBSCRIPTION_FROM_RUIM = 0;
    public static final int SUBSCRIPTION_FROM_NV = 1;*/ //TODO Remove, moved to RILConstants

    //preferredNetworkMode  7 - Global, CDMA Preferred
    //                      4 - CDMA only
    //                      3 - GSM/UMTS only
    static final int preferredNetworkMode = RILConstants.NETWORK_MODE_GLOBAL;

    //cdmaSubscription  0 - Subscription from RUIM, when available
    //                  1 - Subscription from NV
    static final int preferredCdmaSubscription = RILConstants.SUBSCRIPTION_FROM_RUIM;
            
    // preferred TTY mode
    // 0 = disabled
    // 1 = enabled
    static final int preferredTTYMode = RILConstants.CDM_TTY_MODE_DISABLED;

    //***** Class Methods

    public static void makeDefaultPhones(Context context) {
        makeDefaultPhone(context);
    }

    /**
     * FIXME replace this with some other way of making these
     * instances
     */
    public static void makeDefaultPhone(Context context) {
        synchronized(Phone.class) {        
            if (!sMadeDefaults) {  
                sLooper = Looper.myLooper();

                if (sLooper == null) {
                    throw new RuntimeException(
                        "PhoneFactory.makeDefaultPhone must be called from Looper thread");
                }

                int retryCount = 0;
                for(;;) {
                    boolean hasException = false;
                    retryCount ++;

                    try {
                        // use UNIX domain socket to
                        // prevent subsequent initialization
                        new LocalServerSocket("com.android.internal.telephony");
                    } catch (java.io.IOException ex) {
                        hasException = true;
                    }

                    if ( !hasException ) {
                        break;
                    } else if (retryCount > SOCKET_OPEN_MAX_RETRY) {
                        throw new RuntimeException("PhoneFactory probably already running");
                    }else {
                        try {
                            Thread.sleep(SOCKET_OPEN_RETRY_MILLIS);
                        } catch (InterruptedException er) {
                        }
                    }
                }

                sPhoneNotifier = new DefaultPhoneNotifier();

                //Get preferredNetworkMode from Settings.System
                int networkMode = Settings.System.getInt(context.getContentResolver(),
                        Settings.System.PREFERRED_NETWORK_MODE, preferredNetworkMode);
                Log.i(LOG_TAG, "Network Mode set to " + Integer.toString(networkMode));

                //Get preferredNetworkMode from Settings.System
                int cdmaSubscription = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.PREFERRED_CDMA_SUBSCRIPTION, preferredCdmaSubscription); 
                Log.i(LOG_TAG, "Cdma Subscription set to " + Integer.toString(cdmaSubscription));

                //reads the system proprieties and makes commandsinterface
                sCommandsInterface = new RIL(context, networkMode, cdmaSubscription); 

                switch(networkMode) {
                    case RILConstants.NETWORK_MODE_GSM_UMTS:
                        sPhone = new PhoneProxy(new GSMPhone(context, 
                                sCommandsInterface, sPhoneNotifier));
                        Log.i(LOG_TAG, "Creating GSMPhone");
                        break;
                    case RILConstants.NETWORK_MODE_GLOBAL:
                    case RILConstants.NETWORK_MODE_CDMA:
                    default:
                        sPhone = new PhoneProxy(new CDMAPhone(context, 
                                sCommandsInterface, sPhoneNotifier));
                        // Check if the TTY mode is enabled, and enable/disable the icon
                        int enabled = 
                            android.provider.Settings.System.getInt(context.getContentResolver(),
                            android.provider.Settings.System.TTY_MODE_ENABLED, preferredTTYMode);
                        setTTYStatusBarIcon(context, ((enabled != 0) ? true : false));
                        Log.i(LOG_TAG, "Creating CDMAPhone");
                }
                sMadeDefaults = true;
            }
        }
    }

    public static Phone getDefaultPhone() {
        if (sLooper != Looper.myLooper()) {
            throw new RuntimeException(
                "PhoneFactory.getDefaultPhone must be called from Looper thread");
        }
        
        if (!sMadeDefaults) {
            throw new IllegalStateException("Default phones haven't been made yet!");
        }
       return sPhone;
    }

    /**
     * Tells the StatusBar whether the TTY mode is enabled or disabled
     */
    private static void setTTYStatusBarIcon(Context context, boolean enabled) {
        Intent ttyModeChanged = new Intent(TtyIntent.TTY_ENABLED_CHANGE_ACTION);
        ttyModeChanged.putExtra("ttyEnabled", enabled);
        context.sendBroadcast(ttyModeChanged);
    }
    
}


