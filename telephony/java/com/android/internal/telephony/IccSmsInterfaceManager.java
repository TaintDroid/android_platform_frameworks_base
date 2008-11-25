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

import com.android.internal.telephony.IccConstants;
import com.android.internal.telephony.gsm.*; //TO BE REMOVED when Isms goes to telephony

/**
 * SimSmsInterfaceManager to provide an inter-process communication to
 * access Sms in Sim.
 */
public abstract class IccSmsInterfaceManager extends ISms.Stub {
}
