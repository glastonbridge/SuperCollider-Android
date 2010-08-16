
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

SRC := Source/libsndfile
HEADERS := ${LOCAL_PATH}/Headers/libsndfile

LOCAL_MODULE := libsndfile
LOCAL_C_INCLUDES := ${HEADERS}
LOCAL_CFLAGS := -DCORE_FORMATS_ONLY -DSF_ANDROID_DEBUG
LOCAL_LDLIBS := -llog
LOCAL_ARM_MODE   := arm
LOCAL_SRC_FILES := \
    $(SRC)/common.c \
    $(SRC)/file_io.c \
    $(SRC)/command.c \
    $(SRC)/pcm.c \
    $(SRC)/ulaw.c \
    $(SRC)/alaw.c \
    $(SRC)/float32.c \
    $(SRC)/double64.c \
    $(SRC)/ms_adpcm.c \
    $(SRC)/interleave.c \
    $(SRC)/strings.c \
    $(SRC)/dither.c \
    $(SRC)/broadcast.c \
    $(SRC)/audio_detect.c \
    $(SRC)/chunk.c \
    $(SRC)/dwvw.c \
    $(SRC)/sndfile.c \
    $(SRC)/wav.c \
    $(SRC)/wav_w64.c \
    $(SRC)/w64.c \
    $(SRC)/ima_adpcm.c  \
    $(SRC)/aiff.c 
    
include $(BUILD_SHARED_LIBRARY)
