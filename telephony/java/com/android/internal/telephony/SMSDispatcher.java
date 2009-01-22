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

import android.app.Activity;
import android.app.PendingIntent;
import android.app.AlertDialog;
import android.app.PendingIntent.CanceledException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.provider.Telephony;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsMessage;
import android.telephony.ServiceState;
import android.util.Config;
import android.util.Log;
import android.view.WindowManager;

import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.SmsMessageBase;
import com.android.internal.telephony.SmsResponse;
import com.android.internal.util.HexDump;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import com.android.internal.R;

import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;
import static android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE;
import static android.telephony.SmsManager.RESULT_ERROR_NULL_PDU;
import static android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF;


public abstract class SMSDispatcher extends Handler {
    private static final String TAG = "SMS";

    /** Default checking period for SMS sent without user permit */
    private static final int DEFAULT_SMS_CHECK_PERIOD = 3600000;

    /** Default number of SMS sent in checking period without user permit */
    private static final int DEFAULT_SMS_MAX_ALLOWED = 100;

    /** Default timeout for SMS sent query */
    private static final int DEFAULT_SMS_TIMOUEOUT = 6000;

    protected static final int WAP_PDU_TYPE_PUSH = 0x06;

    protected static final int WAP_PDU_TYPE_CONFIRMED_PUSH = 0x07;

    protected static final byte DRM_RIGHTS_XML = (byte)0xca;

    protected static final String DRM_RIGHTS_XML_MIME_TYPE = "application/vnd.oma.drm.rights+xml";

    protected static final byte DRM_RIGHTS_WBXML = (byte)0xcb;

    protected static final String DRM_RIGHTS_WBXML_MIME_TYPE =
            "application/vnd.oma.drm.rights+wbxml";

    protected static final byte WAP_SI_MIME_PORT = (byte)0xae;

    protected static final String WAP_SI_MIME_TYPE = "application/vnd.wap.sic";

    protected static final byte WAP_SL_MIME_PORT = (byte)0xb0;

    protected static final String WAP_SL_MIME_TYPE = "application/vnd.wap.slc";

    protected static final byte WAP_CO_MIME_PORT = (byte)0xb2;

    protected static final String WAP_CO_MIME_TYPE = "application/vnd.wap.coc";

    protected static final int WAP_PDU_SHORT_LENGTH_MAX = 30;

    protected static final int WAP_PDU_LENGTH_QUOTE = 31;

    protected static final String MMS_MIME_TYPE = "application/vnd.wap.mms-message";

    protected static final String[] RAW_PROJECTION = new String[] {
        "pdu",
        "sequence",
    };

    static final int MAIL_SEND_SMS = 1;

    static final protected int EVENT_NEW_SMS = 1;

    static final protected int EVENT_SEND_SMS_COMPLETE = 2;

    /** Retry sending a previously failed SMS message */
    static final protected int EVENT_SEND_RETRY = 3;

    /** Status report received */
    static final protected int EVENT_NEW_SMS_STATUS_REPORT = 5;

    /** SIM/RUIM storage is full */
    static final protected int EVENT_ICC_FULL = 6;

    /** SMS confirm required */
    static final protected int EVENT_POST_ALERT = 7;

    /** Send the user confirmed SMS */
    static final protected int EVENT_SEND_CONFIRMED_SMS = 8;

    /** Alert is timeout */
    static final protected int EVENT_ALERT_TIMEOUT = 9;

    protected Phone mPhone;
    protected Context mContext;
    protected ContentResolver mResolver;
    protected CommandsInterface mCm;

    protected final Uri mRawUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, "raw");

    /** Maximum number of times to retry sending a failed SMS. */
    private static final int MAX_SEND_RETRIES = 3;
    /** Delay before next send attempt on a failed SMS, in milliseconds. */
    private static final int SEND_RETRY_DELAY = 2000; // ms

    /**
     * Message reference for a CONCATENATED_8_BIT_REFERENCE or
     * CONCATENATED_16_BIT_REFERENCE message set.  Should be
     * incremented for each set of concatenated messages.
     */
    protected static int sConcatenatedRef;

    private SmsCounter mCounter;

    private SmsTracker mSTracker;

    private static SmsMessage mSmsMessage;
    private static SmsMessageBase mSmsMessageBase;
    private SmsMessageBase.SubmitPduBase mSubmitPduBase;

    /**
     *  Implement the per-application based SMS control, which only allows
     *  a limit on the number of SMS/MMS messages an app can send in checking
     *  period.
     */
    private class SmsCounter {
        private int mCheckPeriod;
        private int mMaxAllowed;
        private HashMap<String, ArrayList<Long>> mSmsStamp;

        /**
         * Create SmsCounter
         * @param mMax is the number of SMS allowed without user permit
         * @param mPeriod is the checking period
         */
        SmsCounter(int mMax, int mPeriod) {
            mMaxAllowed = mMax;
            mCheckPeriod = mPeriod;
            mSmsStamp = new HashMap<String, ArrayList<Long>> ();
        }

        boolean check(String appName) {
            if (!mSmsStamp.containsKey(appName)) {
                mSmsStamp.put(appName, new ArrayList<Long>());
            }

            return isUnderLimit(mSmsStamp.get(appName));
        }

        private boolean isUnderLimit(ArrayList<Long> sent) {
            Long ct =  System.currentTimeMillis();

            Log.d(TAG, "SMS send size=" + sent.size() + "time=" + ct);

            while (sent.size() > 0 && (ct - sent.get(0)) > mCheckPeriod ) {
                    sent.remove(0);
            }

            if (sent.size() < mMaxAllowed) {
                sent.add(ct);
                return true;
            }
            return false;
        }
    }

    protected SMSDispatcher(PhoneBase phone) {
        mPhone = phone;
        mContext = phone.getContext();
        mResolver = mContext.getContentResolver();
        mCm = phone.mCM;
        mSTracker = null;
        mCounter = new SmsCounter(DEFAULT_SMS_MAX_ALLOWED,
                DEFAULT_SMS_CHECK_PERIOD);

        mCm.setOnNewSMS(this, EVENT_NEW_SMS, null);
        mCm.setOnSmsStatus(this, EVENT_NEW_SMS_STATUS_REPORT, null);
        mCm.setOnIccSmsFull(this, EVENT_ICC_FULL, null);

        // Don't always start message ref at 0.
        sConcatenatedRef = new Random().nextInt(256);
    }

    public void dispose() {
        mCm.unSetOnNewSMS(this);
        mCm.unSetOnSmsStatus(this);
        mCm.unSetOnIccSmsFull(this);
    }

    protected void finalize() {
        Log.d(TAG, "SMSDispatcher finalized");
    }


    /* TODO: Need to figure out how to keep track of status report routing in a
     *       persistent manner. If the phone process restarts (reboot or crash),
     *       we will lose this list and any status reports that come in after
     *       will be dropped.
     */
    /** Sent messages awaiting a delivery status report. */
    protected final ArrayList<SmsTracker> deliveryPendingList = new ArrayList<SmsTracker>();

    /**
     * Handles events coming from the phone stack. Overridden from handler.
     *
     * @param msg the message to handle
     */
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch (msg.what) {
        case EVENT_NEW_SMS:
            // A new SMS has been received by the device
            if (Config.LOGD) {
                Log.d(TAG, "New SMS Message Received");
            }

            SmsMessage sms;

            ar = (AsyncResult) msg.obj;

                // FIXME only acknowledge on store
            acknowledgeLastIncomingSms(true, null);

            if (ar.exception != null) {
                Log.e(TAG, "Exception processing incoming SMS",
                        ar.exception);
                return;
            }

            sms = (SmsMessage) ar.result;
            dispatchMessage(sms.mWrappedSmsMessage);

            break;

        case EVENT_SEND_SMS_COMPLETE:
            // An outbound SMS has been successfully transferred, or failed.
            handleSendComplete((AsyncResult) msg.obj);
            break;

        case EVENT_SEND_RETRY:
            sendSms((SmsTracker) msg.obj);
            break;

        case EVENT_NEW_SMS_STATUS_REPORT:
            handleStatusReport((AsyncResult)msg.obj);
            break;

        case EVENT_ICC_FULL:
            handleIccFull();
            break;

        case EVENT_POST_ALERT:
            handleReachSentLimit((SmsTracker)(msg.obj));
            break;

        case EVENT_ALERT_TIMEOUT:
            ((AlertDialog)(msg.obj)).dismiss();
            msg.obj = null;
            mSTracker = null;
            break;

        case EVENT_SEND_CONFIRMED_SMS:
            if (mSTracker!=null) {
                Log.d(TAG, "Ready to send SMS again.");
                sendSms(mSTracker);
                mSTracker = null;
            }
            break;
        }
    }

    /**
     * Called when SIM_FULL message is received from the RIL.  Notifies interested
     * parties that SIM storage for SMS messages is full.
     */
    private void handleIccFull(){
        // broadcast SIM_FULL intent
        Intent intent = new Intent(Intents.SIM_FULL_ACTION);
        mPhone.getContext().sendBroadcast(intent, "android.permission.RECEIVE_SMS");
    }

    /**
     * Called when a status report is received.  This should correspond to
     * a previously successful SEND.
     *
     * @param ar AsyncResult passed into the message handler.  ar.result should
     *           be a String representing the status report PDU, as ASCII hex.
     */
    protected abstract void handleStatusReport(AsyncResult ar);

    /**
     * Called when SMS send completes. Broadcasts a sentIntent on success.
     * On failure, either sets up retries or broadcasts a sentIntent with
     * the failure in the result code.
     *
     * @param ar AsyncResult passed into the message handler.  ar.result should
     *           an SmsResponse instance if send was successful.  ar.userObj
     *           should be an SmsTracker instance.
     */
    protected void handleSendComplete(AsyncResult ar) {
        SmsTracker tracker = (SmsTracker) ar.userObj;
        PendingIntent sentIntent = tracker.mSentIntent;

        if (ar.exception == null) {
            if (Config.LOGD) {
                Log.d(TAG, "SMS send complete. Broadcasting "
                        + "intent: " + sentIntent);
            }

            if (tracker.mDeliveryIntent != null) {
                // Expecting a status report.  Add it to the list.
                int messageRef = ((SmsResponse)ar.result).messageRef;
                tracker.mMessageRef = messageRef;
                deliveryPendingList.add(tracker);
            }

            if (sentIntent != null) {
                try {
                    sentIntent.send(Activity.RESULT_OK);
                } catch (CanceledException ex) {}
            }
        } else {
            if (Config.LOGD) {
                Log.d(TAG, "SMS send failed");
            }

            int ss = mPhone.getServiceState().getState();

            if (ss != ServiceState.STATE_IN_SERVICE) {
                handleNotInService(ss, tracker);
            } else if ((((CommandException)(ar.exception)).getCommandError()
                    == CommandException.Error.SMS_FAIL_RETRY) &&
                   tracker.mRetryCount < MAX_SEND_RETRIES) {
                // Retry after a delay if needed.
                // TODO: According to TS 23.040, 9.2.3.6, we should resend
                //       with the same TP-MR as the failed message, and
                //       TP-RD set to 1.  However, we don't have a means of
                //       knowing the MR for the failed message (EF_SMSstatus
                //       may or may not have the MR corresponding to this
                //       message, depending on the failure).  Also, in some
                //       implementations this retry is handled by the baseband.
                tracker.mRetryCount++;
                Message retryMsg = obtainMessage(EVENT_SEND_RETRY, tracker);
                sendMessageDelayed(retryMsg, SEND_RETRY_DELAY);
            } else if (tracker.mSentIntent != null) {
                // Done retrying; return an error to the app.
                try {
                    tracker.mSentIntent.send(RESULT_ERROR_GENERIC_FAILURE);
                } catch (CanceledException ex) {}
            }
        }
    }

    /**
     * Handles outbound message when the phone is not in service.
     *
     * @param ss     Current service state.  Valid values are:
     *                  OUT_OF_SERVICE
     *                  EMERGENCY_ONLY
     *                  POWER_OFF
     * @param tracker   An SmsTracker for the current message.
     */
    protected void handleNotInService(int ss, SmsTracker tracker) {
        if (tracker.mSentIntent != null) {
            try {
                if (ss == ServiceState.STATE_POWER_OFF) {
                    tracker.mSentIntent.send(RESULT_ERROR_RADIO_OFF);
                } else {
                    tracker.mSentIntent.send(RESULT_ERROR_NO_SERVICE);
                }
            } catch (CanceledException ex) {}
        }
    }

    /**
     * Dispatches an incoming SMS messages.
     *
     * @param sms the incoming message from the phone
     */
    protected abstract void dispatchMessage(SmsMessageBase sms);


    /**
     * If this is the last part send the parts out to the application, otherwise
     * the part is stored for later processing.
     */
    protected void processMessagePart(SmsMessageBase sms, int referenceNumber,
            int sequence, int count, int destinationPort) {
        // Lookup all other related parts
        StringBuilder where = new StringBuilder("reference_number =");
        where.append(referenceNumber);
        where.append(" AND address = ?");
        String[] whereArgs = new String[] {sms.getOriginatingAddress()};

        byte[][] pdus = null;
        Cursor cursor = null;
        try {
            cursor = mResolver.query(mRawUri, RAW_PROJECTION, where.toString(), whereArgs, null);
            int cursorCount = cursor.getCount();
            if (cursorCount != count - 1) {
                // We don't have all the parts yet, store this one away
                ContentValues values = new ContentValues();
                values.put("date", new Long(sms.getTimestampMillis()));
                values.put("pdu", HexDump.toHexString(sms.getPdu()));
                values.put("address", sms.getOriginatingAddress());
                values.put("reference_number", referenceNumber);
                values.put("count", count);
                values.put("sequence", sequence);
                if (destinationPort != -1) {
                    values.put("destination_port", destinationPort);
                }
                mResolver.insert(mRawUri, values);

                return;
            }

            // All the parts are in place, deal with them
            int pduColumn = cursor.getColumnIndex("pdu");
            int sequenceColumn = cursor.getColumnIndex("sequence");

            pdus = new byte[count][];
            for (int i = 0; i < cursorCount; i++) {
                cursor.moveToNext();
                int cursorSequence = (int)cursor.getLong(sequenceColumn);
                pdus[cursorSequence - 1] = HexDump.hexStringToByteArray(
                        cursor.getString(pduColumn));
            }
            // This one isn't in the DB, so add it
            pdus[sequence - 1] = sms.getPdu();

            // Remove the parts from the database
            mResolver.delete(mRawUri, where.toString(), whereArgs);
        } catch (SQLException e) {
            Log.e(TAG, "Can't access multipart SMS database", e);
            return;  // TODO: NACK the message or something, don't just discard.
        } finally {
            if (cursor != null) cursor.close();
        }

        // Dispatch the PDUs to applications
        switch (destinationPort) {
        case SmsHeader.PORT_WAP_PUSH: {
            // Build up the data stream
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            for (int i = 0; i < count; i++) {
                SmsMessage msg = SmsMessage.createFromPdu(pdus[i]);
                byte[] data = msg.getUserData();
                output.write(data, 0, data.length);
            }

            // Handle the PUSH
            dispatchWapPdu(output.toByteArray());
            break;
        }

        case -1:
            // The messages were not sent to a port
            dispatchPdus(pdus);
            break;

        default:
            // The messages were sent to a port, so concoct a URI for it
            dispatchPortAddressedPdus(pdus, destinationPort);
            break;
        }
    }

    /**
     * Dispatches standard PDUs to interested applications
     *
     * @param pdus The raw PDUs making up the message
     */
    protected void dispatchPdus(byte[][] pdus) {
        Intent intent = new Intent(Intents.SMS_RECEIVED_ACTION);
        intent.putExtra("pdus", pdus);
        mPhone.getContext().sendBroadcast(
                intent, "android.permission.RECEIVE_SMS");
    }

    /**
     * Dispatches port addressed PDUs to interested applications
     *
     * @param pdus The raw PDUs making up the message
     * @param port The destination port of the messages
     */
    protected void dispatchPortAddressedPdus(byte[][] pdus, int port) {
        Uri uri = Uri.parse("sms://localhost:" + port);
        Intent intent = new Intent(Intents.DATA_SMS_RECEIVED_ACTION, uri);
        intent.putExtra("pdus", pdus);
        mPhone.getContext().sendBroadcast(
                intent, "android.permission.RECEIVE_SMS");
    }

    /**
     * Dispatches inbound messages that are in the WAP PDU format. See
     * wap-230-wsp-20010705-a section 8 for details on the WAP PDU format.
     *
     * @param pdu The WAP PDU, made up of one or more SMS PDUs
     */
    protected void dispatchWapPdu(byte[] pdu) {
        int index = 0;
        int transactionId = pdu[index++] & 0xFF;
        int pduType = pdu[index++] & 0xFF;
        int headerLength = 0;

        if ((pduType != WAP_PDU_TYPE_PUSH) &&
                (pduType != WAP_PDU_TYPE_CONFIRMED_PUSH)) {
            Log.w(TAG, "Received non-PUSH WAP PDU. Type = " + pduType);
            return;
        }

        /**
         * Parse HeaderLen(unsigned integer).
         * From wap-230-wsp-20010705-a section 8.1.2
         * The maximum size of a uintvar is 32 bits.
         * So it will be encoded in no more than 5 octets.
         */
        int temp = 0;
        do {
            temp = pdu[index++];
            headerLength = headerLength << 7;
            headerLength |= temp & 0x7F;
        } while ((temp & 0x80) != 0);

        int headerStartIndex = index;

        /**
         * Parse Content-Type.
         * From wap-230-wsp-20010705-a section 8.4.2.24
         *
         * Content-type-value = Constrained-media | Content-general-form
         * Content-general-form = Value-length Media-type
         * Media-type = (Well-known-media | Extension-Media) *(Parameter)
         * Value-length = Short-length | (Length-quote Length)
         * Short-length = <Any octet 0-30>   (octet <= WAP_PDU_SHORT_LENGTH_MAX)
         * Length-quote = <Octet 31>         (WAP_PDU_LENGTH_QUOTE)
         * Length = Uintvar-integer
         */
        // Parse Value-length.
        if ((pdu[index] & 0xff) <= WAP_PDU_SHORT_LENGTH_MAX) {
            // Short-length.
            index++;
        } else if (pdu[index] == WAP_PDU_LENGTH_QUOTE) {
            // Skip Length-quote.
            index++;
            // Skip Length.
            // Now we assume 8bit is enough to store the content-type length.
            index++;
        }
        String mimeType;
        switch (pdu[headerStartIndex]) {
        case DRM_RIGHTS_XML:
            mimeType = DRM_RIGHTS_XML_MIME_TYPE;
            break;
        case DRM_RIGHTS_WBXML:
            mimeType = DRM_RIGHTS_WBXML_MIME_TYPE;
            break;
        case WAP_SI_MIME_PORT:
            // application/vnd.wap.sic
            mimeType = WAP_SI_MIME_TYPE;
            break;
        case WAP_SL_MIME_PORT:
            mimeType = WAP_SL_MIME_TYPE;
            break;
        case WAP_CO_MIME_PORT:
            mimeType = WAP_CO_MIME_TYPE;
            break;
        default:
            int start = index;

            // Skip text-string.
            // Now we assume the mimetype is Extension-Media.
            while (pdu[index++] != '\0') {
                ;
            }
            mimeType = new String(pdu, start, index-start-1);
            break;
        }

        // XXX Skip the remainder of the header for now
        int dataIndex = headerStartIndex + headerLength;
        byte[] data;
        if (pdu[headerStartIndex] == WAP_CO_MIME_PORT) {
            // because GsmSMSDispatcher can't parse push headers "Content-Location" and
            // X-Wap-Content-URI, so pass the whole push to CO application.
            data = pdu;
        } else {
            data = new byte[pdu.length - dataIndex];
            System.arraycopy(pdu, dataIndex, data, 0, data.length);
        }

        // Notify listeners about the WAP PUSH
        Intent intent = new Intent(Intents.WAP_PUSH_RECEIVED_ACTION);
        intent.setType(mimeType);
        intent.putExtra("transactionId", transactionId);
        intent.putExtra("pduType", pduType);
        intent.putExtra("data", data);

        if (mimeType.equals(MMS_MIME_TYPE)) {
            mPhone.getContext().sendBroadcast(
                    intent, "android.permission.RECEIVE_MMS");
        } else {
            mPhone.getContext().sendBroadcast(
                    intent, "android.permission.RECEIVE_WAP_PUSH");
        }
    }

    /**
     * Send a multi-part text based SMS.
     *
     * @param destinationAddress the address to send the message to
     * @param scAddress is the service center address or null to use
     *   the current default SMSC
     * @param parts an <code>ArrayList</code> of strings that, in order,
     *   comprise the original message
     * @param sentIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been sent.
     *   The result code will be <code>Activity.RESULT_OK<code> for success,
     *   or one of these errors:
     *   <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *   <code>RESULT_ERROR_RADIO_OFF</code>
     *   <code>RESULT_ERROR_NULL_PDU</code>.
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntents if not null, an <code>ArrayList</code> of
     *   <code>PendingIntent</code>s (one for each message part) that is
     *   broadcast when the corresponding message part has been delivered
     *   to the recipient.  The raw pdu of the status report is in the
     *   extended data ("pdu").
     */
    protected abstract void sendMultipartText(String destinationAddress, String scAddress,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents);

    /**
     * Send a SMS
     *
     * @param smsc the SMSC to send the message through, or NULL for the
     *  defatult SMSC
     * @param pdu the raw PDU to send
     * @param sentIntent if not NULL this <code>Intent</code> is
     *  broadcast when the message is sucessfully sent, or failed.
     *  The result code will be <code>Activity.RESULT_OK<code> for success,
     *  or one of these errors:
     *  <code>RESULT_ERROR_GENERIC_FAILURE</code>
     *  <code>RESULT_ERROR_RADIO_OFF</code>
     *  <code>RESULT_ERROR_NULL_PDU</code>.
     *  The per-application based SMS control checks sentIntent. If sentIntent
     *  is NULL the caller will be checked against all unknown applicaitons,
     *  which cause smaller number of SMS to be sent in checking period.
     * @param deliveryIntent if not NULL this <code>Intent</code> is
     *  broadcast when the message is delivered to the recipient.  The
     *  raw pdu of the status report is in the extended data ("pdu").
     */
    protected void sendRawPdu(byte[] smsc, byte[] pdu, PendingIntent sentIntent,
            PendingIntent deliveryIntent) {
        if (pdu == null) {
            if (sentIntent != null) {
                try {
                    sentIntent.send(RESULT_ERROR_NULL_PDU);
                } catch (CanceledException ex) {}
            }
            return;
        }

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("smsc", smsc);
        map.put("pdu", pdu);

        SmsTracker tracker = new SmsTracker(map, sentIntent,
                deliveryIntent);
        int ss = mPhone.getServiceState().getState();

        if (ss != ServiceState.STATE_IN_SERVICE) {
            handleNotInService(ss, tracker);
        } else {
            String appName = getAppNameByIntent(sentIntent);
            if (mCounter.check(appName)) {
                sendSms(tracker);
            } else {
                sendMessage(obtainMessage(EVENT_POST_ALERT, tracker));
            }
        }
    }

    /**
     * Post an alert while SMS needs user confirm.
     *
     * An SmsTracker for the current message.
     */
    protected void handleReachSentLimit(SmsTracker tracker) {

        Resources r = Resources.getSystem();

        String appName = getAppNameByIntent(tracker.mSentIntent);

        AlertDialog d = new AlertDialog.Builder(mContext)
                .setTitle(r.getString(R.string.sms_control_title))
                .setMessage(appName + " " + r.getString(R.string.sms_control_message))
                .setPositiveButton(r.getString(R.string.sms_control_yes), mListener)
                .setNegativeButton(r.getString(R.string.sms_control_no), null)
                .create();

        d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        d.show();

        mSTracker = tracker;
        sendMessageDelayed ( obtainMessage(EVENT_ALERT_TIMEOUT, d),
                DEFAULT_SMS_TIMOUEOUT);
    }

    protected String getAppNameByIntent(PendingIntent intent) {
        Resources r = Resources.getSystem();
        return (intent != null) ? intent.getTargetPackage()
            : r.getString(R.string.sms_control_default_app_name);
    }

    /**
     * Send the message along to the radio.
     *
     * @param tracker holds the SMS message to send
     */
    protected abstract void sendSms(SmsTracker tracker);

    /**
     * Activate or deactivate cell broadcast SMS.
     *
     * @param activate
     *            0 = activate, 1 = deactivate
     * @param response
     *            Callback message is empty on completion
     */
    protected abstract void activateCellBroadcastSms(int activate, Message response);

    /**
     * Query the current configuration of cell broadcast SMS.
     *
     * @param response
     *            Callback message contains the configuration from the modem on completion
     *            @see #setCellBroadcastConfig
     */
    protected abstract void getCellBroadcastSmsConfig(Message response);

    /**
     * Configure cell broadcast SMS.
     *
     * @param configValuesArray
     *          The first element defines the number of triples that follow.
     *          A triple is made up of the service category, the language identifier
     *          and a boolean that specifies whether the category is set active.
     * @param response
     *            Callback message is empty on completion
     */
    protected abstract void setCellBroadcastConfig(int[] configValuesArray, Message response);

    /**
     * Send an acknowledge message.
     * @param success indicates that last message was successfully received.
     * @param response callback message sent when operation completes.
     */
    protected abstract void acknowledgeLastIncomingSms(boolean success, Message response);

    /**
     * Keeps track of an SMS that has been sent to the RIL, until it it has
     * successfully been sent, or we're done trying.
     *
     */
    static protected class SmsTracker {
        // fields need to be public for derived SmsDispatchers
        public HashMap mData;
        public int mRetryCount;
        public int mMessageRef;

        public PendingIntent mSentIntent;
        public PendingIntent mDeliveryIntent;

        SmsTracker(HashMap data, PendingIntent sentIntent,
                PendingIntent deliveryIntent) {
            mData = data;
            mSentIntent = sentIntent;
            mDeliveryIntent = deliveryIntent;
            mRetryCount = 0;
        }
    }

    private DialogInterface.OnClickListener mListener =
        new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON1) {
                    Log.d(TAG, "click YES to send out sms");
                    sendMessage(obtainMessage(EVENT_SEND_CONFIRMED_SMS));
                }
            }
        };
}

