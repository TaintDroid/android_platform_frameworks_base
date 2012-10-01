LOCAL_PATH:= $(call my-dir)

#include $(CLEAR_VARS)
#LOCAL_SRC_FILES := bctest.c binder.c
#LOCAL_MODULE := bctest
#include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_SHARED_LIBRARIES := liblog
LOCAL_SRC_FILES := service_manager.c binder.c
LOCAL_MODULE := servicemanager
ifeq ($(WITH_TAINT_TRACKING),true)
LOCAL_CFLAGS += -DWITH_TAINT_TRACKING
endif
ifeq ($(WITH_TAINT_BYTE_PARCEL),true)
LOCAL_CFLAGS += -DWITH_TAINT_BYTE_PARCEL
endif
include $(BUILD_EXECUTABLE)
