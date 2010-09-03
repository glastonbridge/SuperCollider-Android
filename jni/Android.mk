LOCAL_PATH := $(call my-dir)

include ${LOCAL_PATH}/libsndfile.mk

include $(CLEAR_VARS)

LOCAL_LDLIBS    += -L$(SYSROOT)/usr/lib -ldl -llog
LOCAL_SHARED_LIBRARIES = libsndfile
LOCAL_MODULE    := scsynth
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/server
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/plugin_interface
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/common
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/fromscau
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/libc
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/libsndfile
LOCAL_CFLAGS    += -DSC_PLUGIN_EXT=\".so\"
# TODO why doesn't the ndk define __linux__? 
LOCAL_CFLAGS    += -D__linux__
LOCAL_CFLAGS    += -DSC_ANDROID
# This disables a lot of log posting from the audio thread, so improves performance. Comment it out if need debug info.
#LOCAL_CFLAGS    += -DNDEBUG
# TODO there may be a good memory alignment choice for arm: 1 generally ok, maybe 16 when neon.
LOCAL_CFLAGS    += -DSC_MEMORY_ALIGNMENT=1
LOCAL_SRC_FILES := \
    Source/server/Rendezvous.cpp \
    Source/server/Samp.cpp \
    Source/server/SC_BufGen.cpp \
    Source/server/SC_Carbon.cpp \
    Source/server/SC_ComPort.cpp \
    Source/server/SC_Complex.cpp \
    Source/server/SC_CoreAudio.cpp \
    Source/server/SC_Errors.cpp \
    Source/server/SC_Graph.cpp \
    Source/server/SC_GraphDef.cpp \
    Source/server/SC_Group.cpp \
    Source/server/SC_Lib_Cintf.cpp \
    Source/server/SC_Lib.cpp \
    Source/server/SC_MiscCmds.cpp \
    Source/server/SC_Node.cpp \
    Source/server/SC_Rate.cpp \
    Source/server/SC_SequencedCommand.cpp \
    Source/server/SC_Str4.cpp \
    Source/server/SC_SyncCondition.cpp \
    Source/server/scsynth_main.cpp \
    Source/server/SC_Unit.cpp \
    Source/server/SC_UnitDef.cpp \
    Source/server/SC_World.cpp \
    Source/common/SC_Sem.cpp \
    Source/libc/glob.c \
    Source/common/SC_DirUtils.cpp \
    Source/common/SC_StringParser.cpp \
    Source/common/SC_AllocPool.cpp \
    Source/fromscau/OSCMessages.cpp \
    Source/server/SC_Audio_Android.cpp \
    Source/server/SC_Android.cpp 

include $(BUILD_SHARED_LIBRARY)

# plugins:

PLUGINS_DIR := Source/plugins

include $(CLEAR_VARS)
LOCAL_MODULE := FFT_UGens
LOCAL_SRC_FILES := \
    Source/common/SC_fftlib.cpp \
    Source/common/fftlib.c \
    Source/plugins/SCComplex.cpp \
    Source/plugins/FFT_UGens.cpp \
    Source/plugins/PV_UGens.cpp \
    Source/plugins/PartitionedConvolution.cpp \
    Source/plugins/FFTInterfaceTable.cpp
#LOCAL_LDLIBS    += $(LOCAL_PATH)/StaticLibs/libfftw3f.a
#LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/fftw
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/libsndfile
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/plugin_interface
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/common
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/server
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Source/plugins
LOCAL_CFLAGS    += -D__linux__
LOCAL_CFLAGS    += -DSC_ANDROID
LOCAL_CFLAGS    += -DSC_FFT_GREEN=1
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE   := IOUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := NoiseUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := DynNoiseUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := LFUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := OscUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := BinaryOpUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := FilterUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := PanUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := MulAddUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := UnaryOpUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := TriggerUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := DelayUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := DemandUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := GendynUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := GrainUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := ReverbUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := ChaosUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := PhysicalModelingUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := ML_UGens
LOCAL_SRC_FILES := \
    $(PLUGINS_DIR)/BeatTrack.cpp \
    $(PLUGINS_DIR)/BeatTrack2.cpp \
    $(PLUGINS_DIR)/KeyTrack.cpp \
    $(PLUGINS_DIR)/Loudness.cpp \
    $(PLUGINS_DIR)/MFCC.cpp \
    $(PLUGINS_DIR)/ML.cpp \
    $(PLUGINS_DIR)/ML_SpecStats.cpp \
    $(PLUGINS_DIR)/Onsets.cpp \
    $(PLUGINS_DIR)/onsetsds.c \
    Source/plugins/SCComplex.cpp
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/libsndfile
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/plugin_interface
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/common
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/server
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Source/plugins
LOCAL_CFLAGS    += -D__linux__
LOCAL_CFLAGS    += -DSC_ANDROID
LOCAL_CFLAGS    += -DSC_FFT_GREEN=1
include $(BUILD_SHARED_LIBRARY)

#############################################################
# Now some ugens from the "sc3-plugins" extensions collection

PLUGINS_DIR := Source/sc3-plugins

include $(CLEAR_VARS)
LOCAL_MODULE := MCLDBufferUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := MCLDTreeUGens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := MCLDTriggeredStatsUgens
include ${LOCAL_PATH}/simple_ugen.mk

include $(CLEAR_VARS)
LOCAL_MODULE := MCLDFFTUGens
LOCAL_SRC_FILES := \
    $(PLUGINS_DIR)/$(LOCAL_MODULE).cpp \
    Source/plugins/SCComplex.cpp
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/libsndfile
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/plugin_interface
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/common
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/server
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Source/plugins
LOCAL_CFLAGS    += -D__linux__
LOCAL_CFLAGS    += -DSC_ANDROID
LOCAL_CFLAGS    += -DSC_FFT_GREEN=1
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := AY_UGen
LOCAL_SRC_FILES := \
    Source/sc3-plugins/AY_UGen.cpp \
    Source/sc3-plugins/AY_libayemu/src/ay8912.c
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/plugin_interface
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/common
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/server
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Source/sc3-plugins/AY_libayemu/include
LOCAL_CFLAGS    += -DNO_LIBSNDFILE
LOCAL_CFLAGS    += -D__linux__
LOCAL_CFLAGS    += -DSC_ANDROID
include $(BUILD_SHARED_LIBRARY)
