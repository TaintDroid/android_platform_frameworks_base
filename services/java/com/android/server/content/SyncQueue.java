/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.server.content;

import android.accounts.Account;
import android.content.pm.PackageManager;
import android.content.pm.RegisteredServicesCache;
import android.content.SyncAdapterType;
import android.content.SyncAdaptersCache;
import android.content.pm.RegisteredServicesCache.ServiceInfo;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Pair;

import com.google.android.collect.Maps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Queue of pending sync operations. Not inherently thread safe, external
 * callers are responsible for locking.
 *
 * @hide
 */
public class SyncQueue {
    private static final String TAG = "SyncManager";
    private final SyncStorageEngine mSyncStorageEngine;
    private final SyncAdaptersCache mSyncAdapters;
    private final PackageManager mPackageManager;

    // A Map of SyncOperations operationKey -> SyncOperation that is designed for
    // quick lookup of an enqueued SyncOperation.
    private final HashMap<String, SyncOperation> mOperationsMap = Maps.newHashMap();

    public SyncQueue(PackageManager packageManager, SyncStorageEngine syncStorageEngine,
            final SyncAdaptersCache syncAdapters) {
        mPackageManager = packageManager;
        mSyncStorageEngine = syncStorageEngine;
        mSyncAdapters = syncAdapters;
    }

    public void addPendingOperations(int userId) {
        for (SyncStorageEngine.PendingOperation op : mSyncStorageEngine.getPendingOperations()) {
            if (op.userId != userId) continue;

            final Pair<Long, Long> backoff = mSyncStorageEngine.getBackoff(
                    op.account, op.userId, op.authority);
            final ServiceInfo<SyncAdapterType> syncAdapterInfo = mSyncAdapters.getServiceInfo(
                    SyncAdapterType.newKey(op.authority, op.account.type), op.userId);
            if (syncAdapterInfo == null) {
                Log.w(TAG, "Missing sync adapter info for authority " + op.authority + ", userId "
                        + op.userId);
                continue;
            }
            SyncOperation syncOperation = new SyncOperation(
                    op.account, op.userId, op.reason, op.syncSource, op.authority, op.extras,
                    0 /* delay */, backoff != null ? backoff.first : 0,
                    mSyncStorageEngine.getDelayUntilTime(op.account, op.userId, op.authority),
                    syncAdapterInfo.type.allowParallelSyncs());
            syncOperation.expedited = op.expedited;
            syncOperation.pendingOperation = op;
            add(syncOperation, op);
        }
    }

    public boolean add(SyncOperation operation) {
        return add(operation, null /* this is not coming from the database */);
    }

    private boolean add(SyncOperation operation,
            SyncStorageEngine.PendingOperation pop) {
        // - if an operation with the same key exists and this one should run earlier,
        //   update the earliestRunTime of the existing to the new time
        // - if an operation with the same key exists and if this one should run
        //   later, ignore it
        // - if no operation exists then add the new one
        final String operationKey = operation.key;
        final SyncOperation existingOperation = mOperationsMap.get(operationKey);

        if (existingOperation != null) {
            boolean changed = false;
            if (existingOperation.expedited == operation.expedited) {
                final long newRunTime =
                        Math.min(existingOperation.earliestRunTime, operation.earliestRunTime);
                if (existingOperation.earliestRunTime != newRunTime) {
                    existingOperation.earliestRunTime = newRunTime;
                    changed = true;
                }
            } else {
                if (operation.expedited) {
                    existingOperation.expedited = true;
                    changed = true;
                }
            }
            return changed;
        }

        operation.pendingOperation = pop;
        if (operation.pendingOperation == null) {
            pop = new SyncStorageEngine.PendingOperation(
                    operation.account, operation.userId, operation.reason, operation.syncSource,
                    operation.authority, operation.extras, operation.expedited);
            pop = mSyncStorageEngine.insertIntoPending(pop);
            if (pop == null) {
                throw new IllegalStateException("error adding pending sync operation "
                        + operation);
            }
            operation.pendingOperation = pop;
        }

        mOperationsMap.put(operationKey, operation);
        return true;
    }

    public void removeUser(int userId) {
        ArrayList<SyncOperation> opsToRemove = new ArrayList<SyncOperation>();
        for (SyncOperation op : mOperationsMap.values()) {
            if (op.userId == userId) {
                opsToRemove.add(op);
            }
        }

        for (SyncOperation op : opsToRemove) {
            remove(op);
        }
    }

    /**
     * Remove the specified operation if it is in the queue.
     * @param operation the operation to remove
     */
    public void remove(SyncOperation operation) {
        SyncOperation operationToRemove = mOperationsMap.remove(operation.key);
        if (operationToRemove == null) {
            return;
        }
        if (!mSyncStorageEngine.deleteFromPending(operationToRemove.pendingOperation)) {
            final String errorMessage = "unable to find pending row for " + operationToRemove;
            Log.e(TAG, errorMessage, new IllegalStateException(errorMessage));
        }
    }

    public void onBackoffChanged(Account account, int userId, String providerName, long backoff) {
        // for each op that matches the account and provider update its
        // backoff and effectiveStartTime
        for (SyncOperation op : mOperationsMap.values()) {
            if (op.account.equals(account) && op.authority.equals(providerName)
                    && op.userId == userId) {
                op.backoff = backoff;
                op.updateEffectiveRunTime();
            }
        }
    }

    public void onDelayUntilTimeChanged(Account account, String providerName, long delayUntil) {
        // for each op that matches the account and provider update its
        // delayUntilTime and effectiveStartTime
        for (SyncOperation op : mOperationsMap.values()) {
            if (op.account.equals(account) && op.authority.equals(providerName)) {
                op.delayUntil = delayUntil;
                op.updateEffectiveRunTime();
            }
        }
    }

    public void remove(Account account, int userId, String authority) {
        Iterator<Map.Entry<String, SyncOperation>> entries = mOperationsMap.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, SyncOperation> entry = entries.next();
            SyncOperation syncOperation = entry.getValue();
            if (account != null && !syncOperation.account.equals(account)) {
                continue;
            }
            if (authority != null && !syncOperation.authority.equals(authority)) {
                continue;
            }
            if (userId != syncOperation.userId) {
                continue;
            }
            entries.remove();
            if (!mSyncStorageEngine.deleteFromPending(syncOperation.pendingOperation)) {
                final String errorMessage = "unable to find pending row for " + syncOperation;
                Log.e(TAG, errorMessage, new IllegalStateException(errorMessage));
            }
        }
    }

    public Collection<SyncOperation> getOperations() {
        return mOperationsMap.values();
    }

    public void dump(StringBuilder sb) {
        final long now = SystemClock.elapsedRealtime();
        sb.append("SyncQueue: ").append(mOperationsMap.size()).append(" operation(s)\n");
        for (SyncOperation operation : mOperationsMap.values()) {
            sb.append("  ");
            if (operation.effectiveRunTime <= now) {
                sb.append("READY");
            } else {
                sb.append(DateUtils.formatElapsedTime((operation.effectiveRunTime - now) / 1000));
            }
            sb.append(" - ");
            sb.append(operation.dump(mPackageManager, false)).append("\n");
        }
    }
}
