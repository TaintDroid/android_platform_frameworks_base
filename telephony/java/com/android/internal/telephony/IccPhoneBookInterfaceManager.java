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

import android.content.pm.PackageManager;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ServiceManager;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * SimPhoneBookInterfaceManager to provide an inter-process communication to
 * access ADN-like SIM records.
 */
public abstract class IccPhoneBookInterfaceManager extends IIccPhoneBook.Stub {
}
