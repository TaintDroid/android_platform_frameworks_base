/*
 * Copyright (C) 2008-2012 The Android Open Source Project
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

package android.renderscript;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import android.content.res.Resources;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.util.TypedValue;
import android.graphics.Canvas;

/**
 * <p>
 * Memory allocation class for renderscript.  An allocation combines a
 * {@link android.renderscript.Type} with the memory to provide storage for user data and objects.
 * This implies that all memory in Renderscript is typed.
 * </p>
 *
 * <p>Allocations are the primary way data moves into and out of scripts. Memory is user
 * synchronized and it's possible for allocations to exist in multiple memory spaces
 * concurrently. Currently those spaces are:</p>
 * <ul>
 * <li>Script: accessable by RS scripts.</li>
 * <li>Graphics Texture: accessable as a graphics texture.</li>
 * <li>Graphics Vertex: accessable as graphical vertex data.</li>
 * <li>Graphics Constants: Accessable as constants in user shaders</li>
 * </ul>
 * </p>
 * <p>
 * For example, when creating a allocation for a texture, the user can
 * specify its memory spaces as both script and textures. This means that it can both
 * be used as script binding and as a GPU texture for rendering. To maintain
 * synchronization if a script modifies an allocation used by other targets it must
 * call a synchronizing function to push the updates to the memory, otherwise the results
 * are undefined.
 * </p>
 * <p>By default, Android system side updates are always applied to the script accessable
 * memory. If this is not present, they are then applied to the various HW
 * memory types.  A {@link android.renderscript.Allocation#syncAll syncAll()}
 * call is necessary after the script data is updated to
 * keep the other memory spaces in sync.</p>
 *
 * <p>Allocation data is uploaded in one of two primary ways. For simple
 * arrays there are copyFrom() functions that take an array from the control code and
 * copy it to the slave memory store. Both type checked and unchecked copies are provided.
 * The unchecked variants exist to allow apps to copy over arrays of structures from a
 * control language that does not support structures.</p>
 *
 * <div class="special reference">
 * <h3>Developer Guides</h3>
 * <p>For more information about creating an application that uses Renderscript, read the
 * <a href="{@docRoot}guide/topics/renderscript/index.html">Renderscript</a> developer guide.</p>
 * </div>
 **/
public class Allocation extends BaseObj {
    Type mType;
    Bitmap mBitmap;
    int mUsage;
    Allocation mAdaptedAllocation;

    boolean mConstrainedLOD;
    boolean mConstrainedFace;
    boolean mConstrainedY;
    boolean mConstrainedZ;
    boolean mReadAllowed = true;
    boolean mWriteAllowed = true;
    int mSelectedY;
    int mSelectedZ;
    int mSelectedLOD;
    Type.CubemapFace mSelectedFace = Type.CubemapFace.POSITIVE_X;

    int mCurrentDimX;
    int mCurrentDimY;
    int mCurrentDimZ;
    int mCurrentCount;
    static HashMap<Integer, Allocation> mAllocationMap =
            new HashMap<Integer, Allocation>();
    IoInputNotifier mBufferNotifier;


    /**
     * The usage of the allocation.  These signal to renderscript
     * where to place the allocation in memory.
     *
     * SCRIPT The allocation will be bound to and accessed by
     * scripts.
     */
    public static final int USAGE_SCRIPT = 0x0001;

    /**
     * GRAPHICS_TEXTURE The allocation will be used as a texture
     * source by one or more graphics programs.
     *
     */
    public static final int USAGE_GRAPHICS_TEXTURE = 0x0002;

    /**
     * GRAPHICS_VERTEX The allocation will be used as a graphics
     * mesh.
     *
     */
    public static final int USAGE_GRAPHICS_VERTEX = 0x0004;


    /**
     * GRAPHICS_CONSTANTS The allocation will be used as the source
     * of shader constants by one or more programs.
     *
     */
    public static final int USAGE_GRAPHICS_CONSTANTS = 0x0008;

    /**
     * USAGE_GRAPHICS_RENDER_TARGET The allocation will be used as a
     * target for offscreen rendering
     *
     */
    public static final int USAGE_GRAPHICS_RENDER_TARGET = 0x0010;

    /**
     * USAGE_IO_INPUT The allocation will be used as SurfaceTexture
     * consumer.  This usage will cause the allocation to be created
     * read only.
     *
     */
    public static final int USAGE_IO_INPUT = 0x0020;

    /**
     * USAGE_IO_OUTPUT The allocation will be used as a
     * SurfaceTexture producer.  The dimensions and format of the
     * SurfaceTexture will be forced to those of the allocation.
     *
     */
    public static final int USAGE_IO_OUTPUT = 0x0040;

    /**
     * USAGE_SHARED The allocation's backing store will be inherited
     * from another object (usually a Bitmap); calling appropriate
     * copy methods will be significantly faster than if the entire
     * allocation were copied every time.
     *
     * This is set by default for allocations created with
     * CreateFromBitmap(RenderScript, Bitmap) in API version 18 and
     * higher.
     *
     */
    public static final int USAGE_SHARED = 0x0080;

    /**
     * Controls mipmap behavior when using the bitmap creation and
     * update functions.
     */
    public enum MipmapControl {
        /**
         * No mipmaps will be generated and the type generated from the
         * incoming bitmap will not contain additional LODs.
         */
        MIPMAP_NONE(0),

        /**
         * A Full mipmap chain will be created in script memory.  The
         * type of the allocation will contain a full mipmap chain.  On
         * upload to graphics the full chain will be transfered.
         */
        MIPMAP_FULL(1),

        /**
         * The type of the allocation will be the same as MIPMAP_NONE.
         * It will not contain mipmaps.  On upload to graphics the
         * graphics copy of the allocation data will contain a full
         * mipmap chain generated from the top level in script memory.
         */
        MIPMAP_ON_SYNC_TO_TEXTURE(2);

        int mID;
        MipmapControl(int id) {
            mID = id;
        }
    }


    private int getIDSafe() {
        if (mAdaptedAllocation != null) {
            return mAdaptedAllocation.getID(mRS);
        }
        return getID(mRS);
    }


   /**
     * Get the element of the type of the Allocation.
     *
     * @return Element that describes the structure of data in the
     *         allocation
     *
     */
    public Element getElement() {
        return mType.getElement();
    }

    /**
     * Get the usage flags of the Allocation.
     *
     * @return usage flags associated with the allocation. e.g.
     *         script, texture, etc.
     *
     */
    public int getUsage() {
        return mUsage;
    }

    /**
     * Get the size of the Allocation in bytes.
     *
     * @return size of the Allocation in bytes.
     *
     */
    public int getBytesSize() {
        return mType.getCount() * mType.getElement().getBytesSize();
    }

    private void updateCacheInfo(Type t) {
        mCurrentDimX = t.getX();
        mCurrentDimY = t.getY();
        mCurrentDimZ = t.getZ();
        mCurrentCount = mCurrentDimX;
        if (mCurrentDimY > 1) {
            mCurrentCount *= mCurrentDimY;
        }
        if (mCurrentDimZ > 1) {
            mCurrentCount *= mCurrentDimZ;
        }
    }

    private void setBitmap(Bitmap b) {
        mBitmap = b;
    }

    Allocation(int id, RenderScript rs, Type t, int usage) {
        super(id, rs);
        if ((usage & ~(USAGE_SCRIPT |
                       USAGE_GRAPHICS_TEXTURE |
                       USAGE_GRAPHICS_VERTEX |
                       USAGE_GRAPHICS_CONSTANTS |
                       USAGE_GRAPHICS_RENDER_TARGET |
                       USAGE_IO_INPUT |
                       USAGE_IO_OUTPUT |
                       USAGE_SHARED)) != 0) {
            throw new RSIllegalArgumentException("Unknown usage specified.");
        }

        if ((usage & USAGE_IO_INPUT) != 0) {
            mWriteAllowed = false;

            if ((usage & ~(USAGE_IO_INPUT |
                           USAGE_GRAPHICS_TEXTURE |
                           USAGE_SCRIPT)) != 0) {
                throw new RSIllegalArgumentException("Invalid usage combination.");
            }
        }

        mType = t;
        mUsage = usage;

        if (t != null) {
            updateCacheInfo(t);
        }
    }

    private void validateIsInt32() {
        if ((mType.mElement.mType == Element.DataType.SIGNED_32) ||
            (mType.mElement.mType == Element.DataType.UNSIGNED_32)) {
            return;
        }
        throw new RSIllegalArgumentException(
            "32 bit integer source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsInt16() {
        if ((mType.mElement.mType == Element.DataType.SIGNED_16) ||
            (mType.mElement.mType == Element.DataType.UNSIGNED_16)) {
            return;
        }
        throw new RSIllegalArgumentException(
            "16 bit integer source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsInt8() {
        if ((mType.mElement.mType == Element.DataType.SIGNED_8) ||
            (mType.mElement.mType == Element.DataType.UNSIGNED_8)) {
            return;
        }
        throw new RSIllegalArgumentException(
            "8 bit integer source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsFloat32() {
        if (mType.mElement.mType == Element.DataType.FLOAT_32) {
            return;
        }
        throw new RSIllegalArgumentException(
            "32 bit float source does not match allocation type " + mType.mElement.mType);
    }

    private void validateIsObject() {
        if ((mType.mElement.mType == Element.DataType.RS_ELEMENT) ||
            (mType.mElement.mType == Element.DataType.RS_TYPE) ||
            (mType.mElement.mType == Element.DataType.RS_ALLOCATION) ||
            (mType.mElement.mType == Element.DataType.RS_SAMPLER) ||
            (mType.mElement.mType == Element.DataType.RS_SCRIPT) ||
            (mType.mElement.mType == Element.DataType.RS_MESH) ||
            (mType.mElement.mType == Element.DataType.RS_PROGRAM_FRAGMENT) ||
            (mType.mElement.mType == Element.DataType.RS_PROGRAM_VERTEX) ||
            (mType.mElement.mType == Element.DataType.RS_PROGRAM_RASTER) ||
            (mType.mElement.mType == Element.DataType.RS_PROGRAM_STORE)) {
            return;
        }
        throw new RSIllegalArgumentException(
            "Object source does not match allocation type " + mType.mElement.mType);
    }

    @Override
    void updateFromNative() {
        super.updateFromNative();
        int typeID = mRS.nAllocationGetType(getID(mRS));
        if(typeID != 0) {
            mType = new Type(typeID, mRS);
            mType.updateFromNative();
            updateCacheInfo(mType);
        }
    }

    /**
     * Get the type of the Allocation.
     *
     * @return Type
     *
     */
    public Type getType() {
        return mType;
    }

    /**
     * Propagate changes from one usage of the allocation to the
     * remaining usages of the allocation.
     *
     */
    public void syncAll(int srcLocation) {
        switch (srcLocation) {
        case USAGE_GRAPHICS_TEXTURE:
        case USAGE_SCRIPT:
            if ((mUsage & USAGE_SHARED) != 0) {
                copyFrom(mBitmap);
            }
            break;
        case USAGE_GRAPHICS_CONSTANTS:
        case USAGE_GRAPHICS_VERTEX:
            break;
        case USAGE_SHARED:
            if ((mUsage & USAGE_SHARED) != 0) {
                copyTo(mBitmap);
            }
            break;
        default:
            throw new RSIllegalArgumentException("Source must be exactly one usage type.");
        }
        mRS.validate();
        mRS.nAllocationSyncAll(getIDSafe(), srcLocation);
    }

    /**
     * Send a buffer to the output stream.  The contents of the
     * Allocation will be undefined after this operation.
     *
     */
    public void ioSend() {
        if ((mUsage & USAGE_IO_OUTPUT) == 0) {
            throw new RSIllegalArgumentException(
                "Can only send buffer if IO_OUTPUT usage specified.");
        }
        mRS.validate();
        mRS.nAllocationIoSend(getID(mRS));
    }

    /**
     * Delete once code is updated.
     * @hide
     */
    public void ioSendOutput() {
        ioSend();
    }

    /**
     * Receive the latest input into the Allocation.
     *
     */
    public void ioReceive() {
        if ((mUsage & USAGE_IO_INPUT) == 0) {
            throw new RSIllegalArgumentException(
                "Can only receive if IO_INPUT usage specified.");
        }
        mRS.validate();
        mRS.nAllocationIoReceive(getID(mRS));
    }

    /**
     * Copy an array of RS objects to the allocation.
     *
     * @param d Source array.
     */
    public void copyFrom(BaseObj[] d) {
        mRS.validate();
        validateIsObject();
        if (d.length != mCurrentCount) {
            throw new RSIllegalArgumentException("Array size mismatch, allocation sizeX = " +
                                                 mCurrentCount + ", array length = " + d.length);
        }
        int i[] = new int[d.length];
        for (int ct=0; ct < d.length; ct++) {
            i[ct] = d[ct].getID(mRS);
        }
        copy1DRangeFromUnchecked(0, mCurrentCount, i);
    }

    private void validateBitmapFormat(Bitmap b) {
        Bitmap.Config bc = b.getConfig();
        if (bc == null) {
            throw new RSIllegalArgumentException("Bitmap has an unsupported format for this operation");
        }
        switch (bc) {
        case ALPHA_8:
            if (mType.getElement().mKind != Element.DataKind.PIXEL_A) {
                throw new RSIllegalArgumentException("Allocation kind is " +
                                                     mType.getElement().mKind + ", type " +
                                                     mType.getElement().mType +
                                                     " of " + mType.getElement().getBytesSize() +
                                                     " bytes, passed bitmap was " + bc);
            }
            break;
        case ARGB_8888:
            if ((mType.getElement().mKind != Element.DataKind.PIXEL_RGBA) ||
                (mType.getElement().getBytesSize() != 4)) {
                throw new RSIllegalArgumentException("Allocation kind is " +
                                                     mType.getElement().mKind + ", type " +
                                                     mType.getElement().mType +
                                                     " of " + mType.getElement().getBytesSize() +
                                                     " bytes, passed bitmap was " + bc);
            }
            break;
        case RGB_565:
            if ((mType.getElement().mKind != Element.DataKind.PIXEL_RGB) ||
                (mType.getElement().getBytesSize() != 2)) {
                throw new RSIllegalArgumentException("Allocation kind is " +
                                                     mType.getElement().mKind + ", type " +
                                                     mType.getElement().mType +
                                                     " of " + mType.getElement().getBytesSize() +
                                                     " bytes, passed bitmap was " + bc);
            }
            break;
        case ARGB_4444:
            if ((mType.getElement().mKind != Element.DataKind.PIXEL_RGBA) ||
                (mType.getElement().getBytesSize() != 2)) {
                throw new RSIllegalArgumentException("Allocation kind is " +
                                                     mType.getElement().mKind + ", type " +
                                                     mType.getElement().mType +
                                                     " of " + mType.getElement().getBytesSize() +
                                                     " bytes, passed bitmap was " + bc);
            }
            break;

        }
    }

    private void validateBitmapSize(Bitmap b) {
        if((mCurrentDimX != b.getWidth()) || (mCurrentDimY != b.getHeight())) {
            throw new RSIllegalArgumentException("Cannot update allocation from bitmap, sizes mismatch");
        }
    }

    /**
     * Copy an allocation from an array.  This variant is not type
     * checked which allows an application to fill in structured
     * data from an array.
     *
     * @param d the source data array
     */
    public void copyFromUnchecked(int[] d) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFromUnchecked(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ, d);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFromUnchecked(0, 0, mCurrentDimX, mCurrentDimY, d);
        } else {
            copy1DRangeFromUnchecked(0, mCurrentCount, d);
        }
    }
    /**
     * Copy an allocation from an array.  This variant is not type
     * checked which allows an application to fill in structured
     * data from an array.
     *
     * @param d the source data array
     */
    public void copyFromUnchecked(short[] d) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFromUnchecked(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ, d);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFromUnchecked(0, 0, mCurrentDimX, mCurrentDimY, d);
        } else {
            copy1DRangeFromUnchecked(0, mCurrentCount, d);
        }
    }
    /**
     * Copy an allocation from an array.  This variant is not type
     * checked which allows an application to fill in structured
     * data from an array.
     *
     * @param d the source data array
     */
    public void copyFromUnchecked(byte[] d) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFromUnchecked(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ, d);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFromUnchecked(0, 0, mCurrentDimX, mCurrentDimY, d);
        } else {
            copy1DRangeFromUnchecked(0, mCurrentCount, d);
        }
    }
    /**
     * Copy an allocation from an array.  This variant is not type
     * checked which allows an application to fill in structured
     * data from an array.
     *
     * @param d the source data array
     */
    public void copyFromUnchecked(float[] d) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFromUnchecked(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ, d);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFromUnchecked(0, 0, mCurrentDimX, mCurrentDimY, d);
        } else {
            copy1DRangeFromUnchecked(0, mCurrentCount, d);
        }
    }

    /**
     * Copy an allocation from an array.  This variant is type
     * checked and will generate exceptions if the Allocation type
     * is not a 32 bit integer type.
     *
     * @param d the source data array
     */
    public void copyFrom(int[] d) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFrom(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ, d);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFrom(0, 0, mCurrentDimX, mCurrentDimY, d);
        } else {
            copy1DRangeFrom(0, mCurrentCount, d);
        }
    }

    /**
     * Copy an allocation from an array.  This variant is type
     * checked and will generate exceptions if the Allocation type
     * is not a 16 bit integer type.
     *
     * @param d the source data array
     */
    public void copyFrom(short[] d) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFrom(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ, d);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFrom(0, 0, mCurrentDimX, mCurrentDimY, d);
        } else {
            copy1DRangeFrom(0, mCurrentCount, d);
        }
    }

    /**
     * Copy an allocation from an array.  This variant is type
     * checked and will generate exceptions if the Allocation type
     * is not a 8 bit integer type.
     *
     * @param d the source data array
     */
    public void copyFrom(byte[] d) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFrom(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ, d);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFrom(0, 0, mCurrentDimX, mCurrentDimY, d);
        } else {
            copy1DRangeFrom(0, mCurrentCount, d);
        }
    }

    /**
     * Copy an allocation from an array.  This variant is type
     * checked and will generate exceptions if the Allocation type
     * is not a 32 bit float type.
     *
     * @param d the source data array
     */
    public void copyFrom(float[] d) {
        mRS.validate();
        if (mCurrentDimZ > 0) {
            copy3DRangeFrom(0, 0, 0, mCurrentDimX, mCurrentDimY, mCurrentDimZ, d);
        } else if (mCurrentDimY > 0) {
            copy2DRangeFrom(0, 0, mCurrentDimX, mCurrentDimY, d);
        } else {
            copy1DRangeFrom(0, mCurrentCount, d);
        }
    }

    /**
     * Copy an allocation from a bitmap.  The height, width, and
     * format of the bitmap must match the existing allocation.
     *
     * @param b the source bitmap
     */
    public void copyFrom(Bitmap b) {
        mRS.validate();
        if (b.getConfig() == null) {
            Bitmap newBitmap = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(newBitmap);
            c.drawBitmap(b, 0, 0, null);
            copyFrom(newBitmap);
            return;
        }
        validateBitmapSize(b);
        validateBitmapFormat(b);
        mRS.nAllocationCopyFromBitmap(getID(mRS), b);
    }

    /**
     * Copy an allocation from an allocation.  The types of both allocations
     * must be identical.
     *
     * @param a the source allocation
     */
    public void copyFrom(Allocation a) {
        mRS.validate();
        if (!mType.equals(a.getType())) {
            throw new RSIllegalArgumentException("Types of allocations must match.");
        }
        copy2DRangeFrom(0, 0, mCurrentDimX, mCurrentDimY, a, 0, 0);
    }


    /**
     * This is only intended to be used by auto-generate code reflected from the
     * renderscript script files.
     *
     * @param xoff
     * @param fp
     */
    public void setFromFieldPacker(int xoff, FieldPacker fp) {
        mRS.validate();
        int eSize = mType.mElement.getBytesSize();
        final byte[] data = fp.getData();

        int count = data.length / eSize;
        if ((eSize * count) != data.length) {
            throw new RSIllegalArgumentException("Field packer length " + data.length +
                                               " not divisible by element size " + eSize + ".");
        }
        copy1DRangeFromUnchecked(xoff, count, data);
    }

    /**
     * This is only intended to be used by auto-generate code reflected from the
     * renderscript script files.
     *
     * @param xoff
     * @param component_number
     * @param fp
     */
    public void setFromFieldPacker(int xoff, int component_number, FieldPacker fp) {
        mRS.validate();
        if (component_number >= mType.mElement.mElements.length) {
            throw new RSIllegalArgumentException("Component_number " + component_number + " out of range.");
        }
        if(xoff < 0) {
            throw new RSIllegalArgumentException("Offset must be >= 0.");
        }

        final byte[] data = fp.getData();
        int eSize = mType.mElement.mElements[component_number].getBytesSize();
        eSize *= mType.mElement.mArraySizes[component_number];

        if (data.length != eSize) {
            throw new RSIllegalArgumentException("Field packer sizelength " + data.length +
                                               " does not match component size " + eSize + ".");
        }

        mRS.nAllocationElementData1D(getIDSafe(), xoff, mSelectedLOD,
                                     component_number, data, data.length);
    }

    private void data1DChecks(int off, int count, int len, int dataSize) {
        mRS.validate();
        if(off < 0) {
            throw new RSIllegalArgumentException("Offset must be >= 0.");
        }
        if(count < 1) {
            throw new RSIllegalArgumentException("Count must be >= 1.");
        }
        if((off + count) > mCurrentCount) {
            throw new RSIllegalArgumentException("Overflow, Available count " + mCurrentCount +
                                               ", got " + count + " at offset " + off + ".");
        }
        if(len < dataSize) {
            throw new RSIllegalArgumentException("Array too small for allocation type.");
        }
    }

    /**
     * Generate a mipmap chain.  Requires the type of the allocation
     * include mipmaps.
     *
     * This function will generate a complete set of mipmaps from
     * the top level lod and place them into the script memoryspace.
     *
     * If the allocation is also using other memory spaces a
     * followup sync will be required.
     */
    public void generateMipmaps() {
        mRS.nAllocationGenerateMipmaps(getID(mRS));
    }

    /**
     * Copy part of an allocation from an array.  This variant is
     * not type checked which allows an application to fill in
     * structured data from an array.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param d the source data array
     */
    public void copy1DRangeFromUnchecked(int off, int count, int[] d) {
        int dataSize = mType.mElement.getBytesSize() * count;
        data1DChecks(off, count, d.length * 4, dataSize);
        mRS.nAllocationData1D(getIDSafe(), off, mSelectedLOD, count, d, dataSize);
    }
    /**
     * Copy part of an allocation from an array.  This variant is
     * not type checked which allows an application to fill in
     * structured data from an array.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param d the source data array
     */
    public void copy1DRangeFromUnchecked(int off, int count, short[] d) {
        int dataSize = mType.mElement.getBytesSize() * count;
        data1DChecks(off, count, d.length * 2, dataSize);
        mRS.nAllocationData1D(getIDSafe(), off, mSelectedLOD, count, d, dataSize);
    }
    /**
     * Copy part of an allocation from an array.  This variant is
     * not type checked which allows an application to fill in
     * structured data from an array.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param d the source data array
     */
    public void copy1DRangeFromUnchecked(int off, int count, byte[] d) {
        int dataSize = mType.mElement.getBytesSize() * count;
        data1DChecks(off, count, d.length, dataSize);
        mRS.nAllocationData1D(getIDSafe(), off, mSelectedLOD, count, d, dataSize);
    }
    /**
     * Copy part of an allocation from an array.  This variant is
     * not type checked which allows an application to fill in
     * structured data from an array.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param d the source data array
     */
    public void copy1DRangeFromUnchecked(int off, int count, float[] d) {
        int dataSize = mType.mElement.getBytesSize() * count;
        data1DChecks(off, count, d.length * 4, dataSize);
        mRS.nAllocationData1D(getIDSafe(), off, mSelectedLOD, count, d, dataSize);
    }

    /**
     * Copy part of an allocation from an array.  This variant is
     * type checked and will generate exceptions if the Allocation
     * type is not a 32 bit integer type.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param d the source data array
     */
    public void copy1DRangeFrom(int off, int count, int[] d) {
        validateIsInt32();
        copy1DRangeFromUnchecked(off, count, d);
    }

    /**
     * Copy part of an allocation from an array.  This variant is
     * type checked and will generate exceptions if the Allocation
     * type is not a 16 bit integer type.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param d the source data array
     */
    public void copy1DRangeFrom(int off, int count, short[] d) {
        validateIsInt16();
        copy1DRangeFromUnchecked(off, count, d);
    }

    /**
     * Copy part of an allocation from an array.  This variant is
     * type checked and will generate exceptions if the Allocation
     * type is not a 8 bit integer type.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param d the source data array
     */
    public void copy1DRangeFrom(int off, int count, byte[] d) {
        validateIsInt8();
        copy1DRangeFromUnchecked(off, count, d);
    }

    /**
     * Copy part of an allocation from an array.  This variant is
     * type checked and will generate exceptions if the Allocation
     * type is not a 32 bit float type.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param d the source data array.
     */
    public void copy1DRangeFrom(int off, int count, float[] d) {
        validateIsFloat32();
        copy1DRangeFromUnchecked(off, count, d);
    }

     /**
     * Copy part of an allocation from another allocation.
     *
     * @param off The offset of the first element to be copied.
     * @param count The number of elements to be copied.
     * @param data the source data allocation.
     * @param dataOff off The offset of the first element in data to
     *          be copied.
     */
    public void copy1DRangeFrom(int off, int count, Allocation data, int dataOff) {
        mRS.nAllocationData2D(getIDSafe(), off, 0,
                              mSelectedLOD, mSelectedFace.mID,
                              count, 1, data.getID(mRS), dataOff, 0,
                              data.mSelectedLOD, data.mSelectedFace.mID);
    }

    private void validate2DRange(int xoff, int yoff, int w, int h) {
        if (mAdaptedAllocation != null) {

        } else {

            if (xoff < 0 || yoff < 0) {
                throw new RSIllegalArgumentException("Offset cannot be negative.");
            }
            if (h < 0 || w < 0) {
                throw new RSIllegalArgumentException("Height or width cannot be negative.");
            }
            if (((xoff + w) > mCurrentDimX) || ((yoff + h) > mCurrentDimY)) {
                throw new RSIllegalArgumentException("Updated region larger than allocation.");
            }
        }
    }

    void copy2DRangeFromUnchecked(int xoff, int yoff, int w, int h, byte[] data) {
        mRS.validate();
        validate2DRange(xoff, yoff, w, h);
        mRS.nAllocationData2D(getIDSafe(), xoff, yoff, mSelectedLOD, mSelectedFace.mID,
                              w, h, data, data.length);
    }

    void copy2DRangeFromUnchecked(int xoff, int yoff, int w, int h, short[] data) {
        mRS.validate();
        validate2DRange(xoff, yoff, w, h);
        mRS.nAllocationData2D(getIDSafe(), xoff, yoff, mSelectedLOD, mSelectedFace.mID,
                              w, h, data, data.length * 2);
    }

    void copy2DRangeFromUnchecked(int xoff, int yoff, int w, int h, int[] data) {
        mRS.validate();
        validate2DRange(xoff, yoff, w, h);
        mRS.nAllocationData2D(getIDSafe(), xoff, yoff, mSelectedLOD, mSelectedFace.mID,
                              w, h, data, data.length * 4);
    }

    void copy2DRangeFromUnchecked(int xoff, int yoff, int w, int h, float[] data) {
        mRS.validate();
        validate2DRange(xoff, yoff, w, h);
        mRS.nAllocationData2D(getIDSafe(), xoff, yoff, mSelectedLOD, mSelectedFace.mID,
                              w, h, data, data.length * 4);
    }


    /**
     * Copy a rectangular region from the array into the allocation.
     * The incoming array is assumed to be tightly packed.
     *
     * @param xoff X offset of the region to update
     * @param yoff Y offset of the region to update
     * @param w Width of the incoming region to update
     * @param h Height of the incoming region to update
     * @param data to be placed into the allocation
     */
    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, byte[] data) {
        validateIsInt8();
        copy2DRangeFromUnchecked(xoff, yoff, w, h, data);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, short[] data) {
        validateIsInt16();
        copy2DRangeFromUnchecked(xoff, yoff, w, h, data);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, int[] data) {
        validateIsInt32();
        copy2DRangeFromUnchecked(xoff, yoff, w, h, data);
    }

    public void copy2DRangeFrom(int xoff, int yoff, int w, int h, float[] data) {
        validateIsFloat32();
        copy2DRangeFromUnchecked(xoff, yoff, w, h, data);
    }

    /**
     * Copy a rectangular region into the allocation from another
     * allocation.
     *
     * @param xoff X offset of the region to update.
     * @param yoff Y offset of the region to update.
     * @param w Width of the incoming region to update.
     * @param h Height of the incoming region to update.
     * @param data source allocation.
     * @param dataXoff X offset in data of the region to update.
     * @param dataYoff Y offset in data of the region to update.
     */
    public void copy2DRangeFrom(int xoff, int yoff, int w, int h,
                                Allocation data, int dataXoff, int dataYoff) {
        mRS.validate();
        validate2DRange(xoff, yoff, w, h);
        mRS.nAllocationData2D(getIDSafe(), xoff, yoff,
                              mSelectedLOD, mSelectedFace.mID,
                              w, h, data.getID(mRS), dataXoff, dataYoff,
                              data.mSelectedLOD, data.mSelectedFace.mID);
    }

    /**
     * Copy a bitmap into an allocation.  The height and width of
     * the update will use the height and width of the incoming
     * bitmap.
     *
     * @param xoff X offset of the region to update
     * @param yoff Y offset of the region to update
     * @param data the bitmap to be copied
     */
    public void copy2DRangeFrom(int xoff, int yoff, Bitmap data) {
        mRS.validate();
        if (data.getConfig() == null) {
            Bitmap newBitmap = Bitmap.createBitmap(data.getWidth(), data.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(newBitmap);
            c.drawBitmap(data, 0, 0, null);
            copy2DRangeFrom(xoff, yoff, newBitmap);
            return;
        }
        validateBitmapFormat(data);
        validate2DRange(xoff, yoff, data.getWidth(), data.getHeight());
        mRS.nAllocationData2D(getIDSafe(), xoff, yoff, mSelectedLOD, mSelectedFace.mID, data);
    }

    private void validate3DRange(int xoff, int yoff, int zoff, int w, int h, int d) {
        if (mAdaptedAllocation != null) {

        } else {

            if (xoff < 0 || yoff < 0 || zoff < 0) {
                throw new RSIllegalArgumentException("Offset cannot be negative.");
            }
            if (h < 0 || w < 0 || d < 0) {
                throw new RSIllegalArgumentException("Height or width cannot be negative.");
            }
            if (((xoff + w) > mCurrentDimX) || ((yoff + h) > mCurrentDimY) || ((zoff + d) > mCurrentDimZ)) {
                throw new RSIllegalArgumentException("Updated region larger than allocation.");
            }
        }
    }

    /**
     * @hide
     *
     */
    void copy3DRangeFromUnchecked(int xoff, int yoff, int zoff, int w, int h, int d, byte[] data) {
        mRS.validate();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        mRS.nAllocationData3D(getIDSafe(), xoff, yoff, zoff, mSelectedLOD,
                              w, h, d, data, data.length);
    }

    /**
     * @hide
     *
     */
    void copy3DRangeFromUnchecked(int xoff, int yoff, int zoff, int w, int h, int d, short[] data) {
        mRS.validate();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        mRS.nAllocationData3D(getIDSafe(), xoff, yoff, zoff, mSelectedLOD,
                              w, h, d, data, data.length * 2);
    }

    /**
     * @hide
     *
     */
    void copy3DRangeFromUnchecked(int xoff, int yoff, int zoff, int w, int h, int d, int[] data) {
        mRS.validate();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        mRS.nAllocationData3D(getIDSafe(), xoff, yoff, zoff, mSelectedLOD,
                              w, h, d, data, data.length * 4);
    }

    /**
     * @hide
     *
     */
    void copy3DRangeFromUnchecked(int xoff, int yoff, int zoff, int w, int h, int d, float[] data) {
        mRS.validate();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        mRS.nAllocationData3D(getIDSafe(), xoff, yoff, zoff, mSelectedLOD,
                              w, h, d, data, data.length * 4);
    }


    /**
     * @hide
     * Copy a rectangular region from the array into the allocation.
     * The incoming array is assumed to be tightly packed.
     *
     * @param xoff X offset of the region to update
     * @param yoff Y offset of the region to update
     * @param zoff Z offset of the region to update
     * @param w Width of the incoming region to update
     * @param h Height of the incoming region to update
     * @param d Depth of the incoming region to update
     * @param data to be placed into the allocation
     */
    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d, byte[] data) {
        validateIsInt8();
        copy3DRangeFromUnchecked(xoff, yoff, zoff, w, h, d, data);
    }

    /**
     * @hide
     *
     */
    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d, short[] data) {
        validateIsInt16();
        copy3DRangeFromUnchecked(xoff, yoff, zoff, w, h, d, data);
    }

    /**
     * @hide
     *
     */
    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d, int[] data) {
        validateIsInt32();
        copy3DRangeFromUnchecked(xoff, yoff, zoff, w, h, d, data);
    }

    /**
     * @hide
     *
     */
    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d, float[] data) {
        validateIsFloat32();
        copy3DRangeFromUnchecked(xoff, yoff, zoff, w, h, d, data);
    }

    /**
     * @hide
     * Copy a rectangular region into the allocation from another
     * allocation.
     *
     * @param xoff X offset of the region to update.
     * @param yoff Y offset of the region to update.
     * @param w Width of the incoming region to update.
     * @param h Height of the incoming region to update.
     * @param d Depth of the incoming region to update.
     * @param data source allocation.
     * @param dataXoff X offset in data of the region to update.
     * @param dataYoff Y offset in data of the region to update.
     * @param dataZoff Z offset in data of the region to update
     */
    public void copy3DRangeFrom(int xoff, int yoff, int zoff, int w, int h, int d,
                                Allocation data, int dataXoff, int dataYoff, int dataZoff) {
        mRS.validate();
        validate3DRange(xoff, yoff, zoff, w, h, d);
        mRS.nAllocationData3D(getIDSafe(), xoff, yoff, zoff, mSelectedLOD,
                              w, h, d, data.getID(mRS), dataXoff, dataYoff, dataZoff,
                              data.mSelectedLOD);
    }


    /**
     * Copy from the Allocation into a Bitmap.  The bitmap must
     * match the dimensions of the Allocation.
     *
     * @param b The bitmap to be set from the Allocation.
     */
    public void copyTo(Bitmap b) {
        mRS.validate();
        validateBitmapFormat(b);
        validateBitmapSize(b);
        mRS.nAllocationCopyToBitmap(getID(mRS), b);
    }

    /**
     * Copy from the Allocation into a byte array.  The array must
     * be at least as large as the Allocation.  The allocation must
     * be of an 8 bit elemental type.
     *
     * @param d The array to be set from the Allocation.
     */
    public void copyTo(byte[] d) {
        validateIsInt8();
        mRS.validate();
        mRS.nAllocationRead(getID(mRS), d);
    }

    /**
     * Copy from the Allocation into a short array.  The array must
     * be at least as large as the Allocation.  The allocation must
     * be of an 16 bit elemental type.
     *
     * @param d The array to be set from the Allocation.
     */
    public void copyTo(short[] d) {
        validateIsInt16();
        mRS.validate();
        mRS.nAllocationRead(getID(mRS), d);
    }

    /**
     * Copy from the Allocation into a int array.  The array must be
     * at least as large as the Allocation.  The allocation must be
     * of an 32 bit elemental type.
     *
     * @param d The array to be set from the Allocation.
     */
    public void copyTo(int[] d) {
        validateIsInt32();
        mRS.validate();
        mRS.nAllocationRead(getID(mRS), d);
    }

    /**
     * Copy from the Allocation into a float array.  The array must
     * be at least as large as the Allocation.  The allocation must
     * be of an 32 bit float elemental type.
     *
     * @param d The array to be set from the Allocation.
     */
    public void copyTo(float[] d) {
        validateIsFloat32();
        mRS.validate();
        mRS.nAllocationRead(getID(mRS), d);
    }

    /**
     * Resize a 1D allocation.  The contents of the allocation are
     * preserved.  If new elements are allocated objects are created
     * with null contents and the new region is otherwise undefined.
     *
     * If the new region is smaller the references of any objects
     * outside the new region will be released.
     *
     * A new type will be created with the new dimension.
     *
     * @param dimX The new size of the allocation.
     *
     * @deprecated Renderscript objects should be immutable once
     * created.  The replacement is to create a new allocation and copy the
     * contents.
     */
    public synchronized void resize(int dimX) {
        if ((mType.getY() > 0)|| (mType.getZ() > 0) || mType.hasFaces() || mType.hasMipmaps()) {
            throw new RSInvalidStateException("Resize only support for 1D allocations at this time.");
        }
        mRS.nAllocationResize1D(getID(mRS), dimX);
        mRS.finish();  // Necessary because resize is fifoed and update is async.

        int typeID = mRS.nAllocationGetType(getID(mRS));
        mType = new Type(typeID, mRS);
        mType.updateFromNative();
        updateCacheInfo(mType);
    }


    // creation

    static BitmapFactory.Options mBitmapOptions = new BitmapFactory.Options();
    static {
        mBitmapOptions.inScaled = false;
    }

    /**
     *
     * @param type renderscript type describing data layout
     * @param mips specifies desired mipmap behaviour for the
     *             allocation
     * @param usage bit field specifying how the allocation is
     *              utilized
     */
    static public Allocation createTyped(RenderScript rs, Type type, MipmapControl mips, int usage) {
        rs.validate();
        if (type.getID(rs) == 0) {
            throw new RSInvalidStateException("Bad Type");
        }
        int id = rs.nAllocationCreateTyped(type.getID(rs), mips.mID, usage, 0);
        if (id == 0) {
            throw new RSRuntimeException("Allocation creation failed.");
        }
        return new Allocation(id, rs, type, usage);
    }

    /**
     * Creates a renderscript allocation with the size specified by
     * the type and no mipmaps generated by default
     *
     * @param rs Context to which the allocation will belong.
     * @param type renderscript type describing data layout
     * @param usage bit field specifying how the allocation is
     *              utilized
     *
     * @return allocation
     */
    static public Allocation createTyped(RenderScript rs, Type type, int usage) {
        return createTyped(rs, type, MipmapControl.MIPMAP_NONE, usage);
    }

    /**
     * Creates a renderscript allocation for use by the script with
     * the size specified by the type and no mipmaps generated by
     * default
     *
     * @param rs Context to which the allocation will belong.
     * @param type renderscript type describing data layout
     *
     * @return allocation
     */
    static public Allocation createTyped(RenderScript rs, Type type) {
        return createTyped(rs, type, MipmapControl.MIPMAP_NONE, USAGE_SCRIPT);
    }

    /**
     * Creates a renderscript allocation with a specified number of
     * given elements
     *
     * @param rs Context to which the allocation will belong.
     * @param e describes what each element of an allocation is
     * @param count specifies the number of element in the allocation
     * @param usage bit field specifying how the allocation is
     *              utilized
     *
     * @return allocation
     */
    static public Allocation createSized(RenderScript rs, Element e,
                                         int count, int usage) {
        rs.validate();
        Type.Builder b = new Type.Builder(rs, e);
        b.setX(count);
        Type t = b.create();

        int id = rs.nAllocationCreateTyped(t.getID(rs), MipmapControl.MIPMAP_NONE.mID, usage, 0);
        if (id == 0) {
            throw new RSRuntimeException("Allocation creation failed.");
        }
        return new Allocation(id, rs, t, usage);
    }

    /**
     * Creates a renderscript allocation with a specified number of
     * given elements
     *
     * @param rs Context to which the allocation will belong.
     * @param e describes what each element of an allocation is
     * @param count specifies the number of element in the allocation
     *
     * @return allocation
     */
    static public Allocation createSized(RenderScript rs, Element e, int count) {
        return createSized(rs, e, count, USAGE_SCRIPT);
    }

    static Element elementFromBitmap(RenderScript rs, Bitmap b) {
        final Bitmap.Config bc = b.getConfig();
        if (bc == Bitmap.Config.ALPHA_8) {
            return Element.A_8(rs);
        }
        if (bc == Bitmap.Config.ARGB_4444) {
            return Element.RGBA_4444(rs);
        }
        if (bc == Bitmap.Config.ARGB_8888) {
            return Element.RGBA_8888(rs);
        }
        if (bc == Bitmap.Config.RGB_565) {
            return Element.RGB_565(rs);
        }
        throw new RSInvalidStateException("Bad bitmap type: " + bc);
    }

    static Type typeFromBitmap(RenderScript rs, Bitmap b,
                                       MipmapControl mip) {
        Element e = elementFromBitmap(rs, b);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(b.getWidth());
        tb.setY(b.getHeight());
        tb.setMipmaps(mip == MipmapControl.MIPMAP_FULL);
        return tb.create();
    }

    /**
     * Creates a renderscript allocation from a bitmap
     *
     * @param rs Context to which the allocation will belong.
     * @param b bitmap source for the allocation data
     * @param mips specifies desired mipmap behaviour for the
     *             allocation
     * @param usage bit field specifying how the allocation is
     *              utilized
     *
     * @return renderscript allocation containing bitmap data
     *
     */
    static public Allocation createFromBitmap(RenderScript rs, Bitmap b,
                                              MipmapControl mips,
                                              int usage) {
        rs.validate();

        // WAR undocumented color formats
        if (b.getConfig() == null) {
            if ((usage & USAGE_SHARED) != 0) {
                throw new RSIllegalArgumentException("USAGE_SHARED cannot be used with a Bitmap that has a null config.");
            }
            Bitmap newBitmap = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(newBitmap);
            c.drawBitmap(b, 0, 0, null);
            return createFromBitmap(rs, newBitmap, mips, usage);
        }

        Type t = typeFromBitmap(rs, b, mips);

        // enable optimized bitmap path only with no mipmap and script-only usage
        if (mips == MipmapControl.MIPMAP_NONE &&
            t.getElement().isCompatible(Element.RGBA_8888(rs)) &&
            usage == (USAGE_SHARED | USAGE_SCRIPT | USAGE_GRAPHICS_TEXTURE)) {
            int id = rs.nAllocationCreateBitmapBackedAllocation(t.getID(rs), mips.mID, b, usage);
            if (id == 0) {
                throw new RSRuntimeException("Load failed.");
            }

            // keep a reference to the Bitmap around to prevent GC
            Allocation alloc = new Allocation(id, rs, t, usage);
            alloc.setBitmap(b);
            return alloc;
        }


        int id = rs.nAllocationCreateFromBitmap(t.getID(rs), mips.mID, b, usage);
        if (id == 0) {
            throw new RSRuntimeException("Load failed.");
        }
        return new Allocation(id, rs, t, usage);
    }

    /**
     * For allocations used with io operations, returns the handle
     * onto a raw buffer that is being managed by the screen
     * compositor.
     *
     * @return Surface object associated with allocation
     *
     */
    public Surface getSurface() {
        if ((mUsage & USAGE_IO_INPUT) == 0) {
            throw new RSInvalidStateException("Allocation is not a surface texture.");
        }
        return mRS.nAllocationGetSurface(getID(mRS));
    }

    /**
     * @hide
     */
    public void setSurfaceTexture(SurfaceTexture st) {
        setSurface(new Surface(st));
    }

    /**
     * Associate a surface for io output with this allocation
     *
     * @param sur Surface to associate with allocation
     */
    public void setSurface(Surface sur) {
        mRS.validate();
        if ((mUsage & USAGE_IO_OUTPUT) == 0) {
            throw new RSInvalidStateException("Allocation is not USAGE_IO_OUTPUT.");
        }

        mRS.nAllocationSetSurface(getID(mRS), sur);
    }

    /**
     * Creates a RenderScript allocation from a bitmap.
     *
     * With target API version 18 or greater, this allocation will be
     * created with USAGE_SHARED. With target API version 17 or lower,
     * this allocation will be created with USAGE_GRAPHICS_TEXTURE.
     *
     * @param rs Context to which the allocation will belong.
     * @param b bitmap source for the allocation data
     *
     * @return renderscript allocation containing bitmap data
     *
     */
    static public Allocation createFromBitmap(RenderScript rs, Bitmap b) {
        if (rs.getApplicationContext().getApplicationInfo().targetSdkVersion >= 18) {
            return createFromBitmap(rs, b, MipmapControl.MIPMAP_NONE,
                                    USAGE_SHARED | USAGE_SCRIPT | USAGE_GRAPHICS_TEXTURE);
        }
        return createFromBitmap(rs, b, MipmapControl.MIPMAP_NONE,
                                USAGE_GRAPHICS_TEXTURE);
    }

    /**
     * Creates a cubemap allocation from a bitmap containing the
     * horizontal list of cube faces. Each individual face must be
     * the same size and power of 2
     *
     * @param rs Context to which the allocation will belong.
     * @param b bitmap with cubemap faces layed out in the following
     *          format: right, left, top, bottom, front, back
     * @param mips specifies desired mipmap behaviour for the cubemap
     * @param usage bit field specifying how the cubemap is utilized
     *
     * @return allocation containing cubemap data
     *
     */
    static public Allocation createCubemapFromBitmap(RenderScript rs, Bitmap b,
                                                     MipmapControl mips,
                                                     int usage) {
        rs.validate();

        int height = b.getHeight();
        int width = b.getWidth();

        if (width % 6 != 0) {
            throw new RSIllegalArgumentException("Cubemap height must be multiple of 6");
        }
        if (width / 6 != height) {
            throw new RSIllegalArgumentException("Only square cube map faces supported");
        }
        boolean isPow2 = (height & (height - 1)) == 0;
        if (!isPow2) {
            throw new RSIllegalArgumentException("Only power of 2 cube faces supported");
        }

        Element e = elementFromBitmap(rs, b);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(height);
        tb.setY(height);
        tb.setFaces(true);
        tb.setMipmaps(mips == MipmapControl.MIPMAP_FULL);
        Type t = tb.create();

        int id = rs.nAllocationCubeCreateFromBitmap(t.getID(rs), mips.mID, b, usage);
        if(id == 0) {
            throw new RSRuntimeException("Load failed for bitmap " + b + " element " + e);
        }
        return new Allocation(id, rs, t, usage);
    }

    /**
     * Creates a non-mipmapped cubemap allocation for use as a
     * graphics texture from a bitmap containing the horizontal list
     * of cube faces. Each individual face must be the same size and
     * power of 2
     *
     * @param rs Context to which the allocation will belong.
     * @param b bitmap with cubemap faces layed out in the following
     *          format: right, left, top, bottom, front, back
     *
     * @return allocation containing cubemap data
     *
     */
    static public Allocation createCubemapFromBitmap(RenderScript rs,
                                                     Bitmap b) {
        return createCubemapFromBitmap(rs, b, MipmapControl.MIPMAP_NONE,
                                       USAGE_GRAPHICS_TEXTURE);
    }

    /**
     * Creates a cubemap allocation from 6 bitmaps containing
     * the cube faces. All the faces must be the same size and
     * power of 2
     *
     * @param rs Context to which the allocation will belong.
     * @param xpos cubemap face in the positive x direction
     * @param xneg cubemap face in the negative x direction
     * @param ypos cubemap face in the positive y direction
     * @param yneg cubemap face in the negative y direction
     * @param zpos cubemap face in the positive z direction
     * @param zneg cubemap face in the negative z direction
     * @param mips specifies desired mipmap behaviour for the cubemap
     * @param usage bit field specifying how the cubemap is utilized
     *
     * @return allocation containing cubemap data
     *
     */
    static public Allocation createCubemapFromCubeFaces(RenderScript rs,
                                                        Bitmap xpos,
                                                        Bitmap xneg,
                                                        Bitmap ypos,
                                                        Bitmap yneg,
                                                        Bitmap zpos,
                                                        Bitmap zneg,
                                                        MipmapControl mips,
                                                        int usage) {
        int height = xpos.getHeight();
        if (xpos.getWidth() != height ||
            xneg.getWidth() != height || xneg.getHeight() != height ||
            ypos.getWidth() != height || ypos.getHeight() != height ||
            yneg.getWidth() != height || yneg.getHeight() != height ||
            zpos.getWidth() != height || zpos.getHeight() != height ||
            zneg.getWidth() != height || zneg.getHeight() != height) {
            throw new RSIllegalArgumentException("Only square cube map faces supported");
        }
        boolean isPow2 = (height & (height - 1)) == 0;
        if (!isPow2) {
            throw new RSIllegalArgumentException("Only power of 2 cube faces supported");
        }

        Element e = elementFromBitmap(rs, xpos);
        Type.Builder tb = new Type.Builder(rs, e);
        tb.setX(height);
        tb.setY(height);
        tb.setFaces(true);
        tb.setMipmaps(mips == MipmapControl.MIPMAP_FULL);
        Type t = tb.create();
        Allocation cubemap = Allocation.createTyped(rs, t, mips, usage);

        AllocationAdapter adapter = AllocationAdapter.create2D(rs, cubemap);
        adapter.setFace(Type.CubemapFace.POSITIVE_X);
        adapter.copyFrom(xpos);
        adapter.setFace(Type.CubemapFace.NEGATIVE_X);
        adapter.copyFrom(xneg);
        adapter.setFace(Type.CubemapFace.POSITIVE_Y);
        adapter.copyFrom(ypos);
        adapter.setFace(Type.CubemapFace.NEGATIVE_Y);
        adapter.copyFrom(yneg);
        adapter.setFace(Type.CubemapFace.POSITIVE_Z);
        adapter.copyFrom(zpos);
        adapter.setFace(Type.CubemapFace.NEGATIVE_Z);
        adapter.copyFrom(zneg);

        return cubemap;
    }

    /**
     * Creates a non-mipmapped cubemap allocation for use as a
     * graphics texture from 6 bitmaps containing
     * the cube faces. All the faces must be the same size and
     * power of 2
     *
     * @param rs Context to which the allocation will belong.
     * @param xpos cubemap face in the positive x direction
     * @param xneg cubemap face in the negative x direction
     * @param ypos cubemap face in the positive y direction
     * @param yneg cubemap face in the negative y direction
     * @param zpos cubemap face in the positive z direction
     * @param zneg cubemap face in the negative z direction
     *
     * @return allocation containing cubemap data
     *
     */
    static public Allocation createCubemapFromCubeFaces(RenderScript rs,
                                                        Bitmap xpos,
                                                        Bitmap xneg,
                                                        Bitmap ypos,
                                                        Bitmap yneg,
                                                        Bitmap zpos,
                                                        Bitmap zneg) {
        return createCubemapFromCubeFaces(rs, xpos, xneg, ypos, yneg,
                                          zpos, zneg, MipmapControl.MIPMAP_NONE,
                                          USAGE_GRAPHICS_TEXTURE);
    }

    /**
     * Creates a renderscript allocation from the bitmap referenced
     * by resource id
     *
     * @param rs Context to which the allocation will belong.
     * @param res application resources
     * @param id resource id to load the data from
     * @param mips specifies desired mipmap behaviour for the
     *             allocation
     * @param usage bit field specifying how the allocation is
     *              utilized
     *
     * @return renderscript allocation containing resource data
     *
     */
    static public Allocation createFromBitmapResource(RenderScript rs,
                                                      Resources res,
                                                      int id,
                                                      MipmapControl mips,
                                                      int usage) {

        rs.validate();
        if ((usage & (USAGE_SHARED | USAGE_IO_INPUT | USAGE_IO_OUTPUT)) != 0) {
            throw new RSIllegalArgumentException("Unsupported usage specified.");
        }
        Bitmap b = BitmapFactory.decodeResource(res, id);
        Allocation alloc = createFromBitmap(rs, b, mips, usage);
        b.recycle();
        return alloc;
    }

    /**
     * Creates a non-mipmapped renderscript allocation to use as a
     * graphics texture from the bitmap referenced by resource id
     *
     * With target API version 18 or greater, this allocation will be
     * created with USAGE_SHARED. With target API version 17 or lower,
     * this allocation will be created with USAGE_GRAPHICS_TEXTURE.
     *
     * @param rs Context to which the allocation will belong.
     * @param res application resources
     * @param id resource id to load the data from
     *
     * @return renderscript allocation containing resource data
     *
     */
    static public Allocation createFromBitmapResource(RenderScript rs,
                                                      Resources res,
                                                      int id) {
        if (rs.getApplicationContext().getApplicationInfo().targetSdkVersion >= 18) {
            return createFromBitmapResource(rs, res, id,
                                            MipmapControl.MIPMAP_NONE,
                                            USAGE_SCRIPT | USAGE_GRAPHICS_TEXTURE);
        }
        return createFromBitmapResource(rs, res, id,
                                        MipmapControl.MIPMAP_NONE,
                                        USAGE_GRAPHICS_TEXTURE);
    }

    /**
     * Creates a renderscript allocation containing string data
     * encoded in UTF-8 format
     *
     * @param rs Context to which the allocation will belong.
     * @param str string to create the allocation from
     * @param usage bit field specifying how the allocaiton is
     *              utilized
     *
     */
    static public Allocation createFromString(RenderScript rs,
                                              String str,
                                              int usage) {
        rs.validate();
        byte[] allocArray = null;
        try {
            allocArray = str.getBytes("UTF-8");
            Allocation alloc = Allocation.createSized(rs, Element.U8(rs), allocArray.length, usage);
            alloc.copyFrom(allocArray);
            return alloc;
        }
        catch (Exception e) {
            throw new RSRuntimeException("Could not convert string to utf-8.");
        }
    }

    /**
     * @hide
     *
     * Interface to handle notification when new buffers are
     * available via USAGE_IO_INPUT.  An application will receive
     * one notification when a buffer is available.  Additional
     * buffers will not trigger new notifications until a buffer is
     * processed.
     */
    public interface IoInputNotifier {
        public void onBufferAvailable(Allocation a);
    }

    /**
     * @hide
     *
     * Set a notification handler for USAGE_IO_INPUT
     *
     * @param callback instance of the IoInputNotifier class to be called
     *                 when buffer arrive.
     */
    public void setIoInputNotificationHandler(IoInputNotifier callback) {
        synchronized(mAllocationMap) {
            mAllocationMap.put(new Integer(getID(mRS)), this);
            mBufferNotifier = callback;
        }
    }

    static void sendBufferNotification(int id) {
        synchronized(mAllocationMap) {
            Allocation a = mAllocationMap.get(new Integer(id));

            if ((a != null) && (a.mBufferNotifier != null)) {
                a.mBufferNotifier.onBufferAvailable(a);
            }
        }
    }

}


