/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef ANDROID_HWUI_DEFERRED_DISPLAY_LIST_H
#define ANDROID_HWUI_DEFERRED_DISPLAY_LIST_H

#include <utils/Errors.h>
#include <utils/Vector.h>

#include "Matrix.h"
#include "Rect.h"
#include "utils/TinyHashMap.h"

class SkBitmap;

namespace android {
namespace uirenderer {

class ClipOp;
class DrawOp;
class SaveOp;
class SaveLayerOp;
class StateOp;
class OpenGLRenderer;

class Batch;
class DrawBatch;
class MergingDrawBatch;

typedef void* mergeid_t;

class DeferredDisplayList {
public:
    DeferredDisplayList() { clear(); }
    ~DeferredDisplayList() { clear(); }

    enum OpBatchId {
        kOpBatch_None = 0, // Don't batch
        kOpBatch_Bitmap,
        kOpBatch_Patch,
        kOpBatch_AlphaVertices,
        kOpBatch_Vertices,
        kOpBatch_AlphaMaskTexture,
        kOpBatch_Text,
        kOpBatch_ColorText,

        kOpBatch_Count, // Add other batch ids before this
    };

    bool isEmpty() { return mBatches.isEmpty(); }

    /**
     * Plays back all of the draw ops recorded into batches to the renderer.
     * Adjusts the state of the renderer as necessary, and restores it when complete
     */
    status_t flush(OpenGLRenderer& renderer, Rect& dirty);

    void addClip(OpenGLRenderer& renderer, ClipOp* op);
    void addSaveLayer(OpenGLRenderer& renderer, SaveLayerOp* op, int newSaveCount);
    void addSave(OpenGLRenderer& renderer, SaveOp* op, int newSaveCount);
    void addRestoreToCount(OpenGLRenderer& renderer, StateOp* op, int newSaveCount);

    /**
     * Add a draw op into the DeferredDisplayList, reordering as needed (for performance) if
     * disallowReorder is false, respecting draw order when overlaps occur
     */
    void addDrawOp(OpenGLRenderer& renderer, DrawOp* op);

private:
    /**
     * Resets the batching back-pointers, creating a barrier in the operation stream so that no ops
     * added in the future will be inserted into a batch that already exist.
     */
    void resetBatchingState();

    void clear();

    void storeStateOpBarrier(OpenGLRenderer& renderer, StateOp* op);
    void storeRestoreToCountBarrier(OpenGLRenderer& renderer, StateOp* op, int newSaveCount);

    bool recordingComplexClip() const { return mComplexClipStackStart >= 0; }

    int getStateOpDeferFlags() const;
    int getDrawOpDeferFlags() const;

    /**
     * At defer time, stores the *defer time* savecount of save/saveLayer ops that were deferred, so
     * that when an associated restoreToCount is deferred, it can be recorded as a
     * RestoreToCountBatch
     */
    Vector<int> mSaveStack;
    int mComplexClipStackStart;

    Vector<Batch*> mBatches;

    // Maps batch ids to the most recent *non-merging* batch of that id
    Batch* mBatchLookup[kOpBatch_Count];

    // Points to the index after the most recent barrier
    int mEarliestBatchIndex;

    /**
     * Maps the mergeid_t returned by an op's getMergeId() to the most recently seen
     * MergingDrawBatch of that id. These ids are unique per draw type and guaranteed to not
     * collide, which avoids the need to resolve mergeid collisions.
     */
    TinyHashMap<mergeid_t, DrawBatch*> mMergingBatches[kOpBatch_Count];
};

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_HWUI_DEFERRED_DISPLAY_LIST_H
