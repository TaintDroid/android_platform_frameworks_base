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

package android.telephony.cdma;

/*import android.os.RemoteException;
import android.util.Log;
import com.android.internal.telephony.Phone;
import android.os.Message;
import android.os.Handler;
import android.os.AsyncResult;
*/

public class TtyIntent {
    
    private static final String TAG = "TtyIntent";
    
//    private Phone mPhone;
//    private boolean mTTYenabled = false;
    
    /** Event for TTY mode change */
//    private static final int EVENT_TTY_EXECUTED             = 1;
    
    /**
     * Broadcast intent action indicating that the TTY has either been
     * enabled or disabled. An intent extra provides this state as a boolean,
     * where {@code true} means enabled.
     * @see #TTY_ENABLED
     *
     * {@hide}
     */
    public static final String TTY_ENABLED_CHANGE_ACTION =
        "android.telephony.cdma.intent.action.TTY_ENABLED_CHANGE";
    
    /**
     * The lookup key for a boolean that indicates whether TTY mode is enabled or
     * disabled. {@code true} means TTY mode is enabled. Retrieve it with
     * {@link android.content.Intent#getBooleanExtra(String,boolean)}.
     *
     * {@hide}
     */
    public static final String TTY_ENABLED = "ttyEnabled";
    
    /**
     * Get the current status of TTY status.
     *
     * @return true if TTY Mode enabled, false otherwise.
     */
/*    public boolean isEnabled() {
        mPhone.queryTTYModeEnabled(Message.obtain(mQueryTTYComplete, EVENT_TTY_EXECUTED));

        return mTTYenabled;
    }
    
    private Handler mQueryTTYComplete = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_TTY_EXECUTED:
                    //handleQueryTTYModeMessage((AsyncResult) msg.obj);
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        Log.w(TAG, "handleMessage: Error getting TTY enable state.");
                    } 
                    else {
                      int ttyArray[] = (int[]) ar.result;
                      if (ttyArray[0] == 1) {
                          //TTY Mode enabled
                          mTTYenabled = true;
                          Log.w(TAG, "handleMessage: mTTYenabled: " + mTTYenabled);
                      }
                      else {
                          // TTY disabled
                          mTTYenabled = false;
                          Log.w(TAG, "handleMessage: mTTYenabled: " + mTTYenabled);
                      }
                    }
                    break;
                default:
                    // TODO: should never reach this, may want to throw exception
            }
        }
    };
    */
}
