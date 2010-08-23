LOCAL_SRC_FILES := \
    $(PLUGINS_DIR)/$(LOCAL_MODULE).cpp
LOCAL_SHARED_LIBRARIES = sndfile
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/plugin_interface
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/common
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/server
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/libsndfile
# TODO why doesn't the ndk define __linux__? 
LOCAL_CFLAGS    += -D__linux__
LOCAL_CFLAGS    += -DSC_ANDROID
include $(BUILD_SHARED_LIBRARY)

