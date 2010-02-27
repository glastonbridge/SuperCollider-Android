# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_LDLIBS    += -L$(SYSROOT)/usr/lib -ldl -llog
LOCAL_MODULE    := scsynth
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/server
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/plugin_interface
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/common
LOCAL_C_INCLUDES+= $(LOCAL_PATH)/Headers/libc
LOCAL_CFLAGS    += -DSC_AUDIO_API=NONE
LOCAL_CFLAGS    += -DNO_LIBSNDFILE
LOCAL_CFLAGS    += -DSC_LINUX
LOCAL_CFLAGS    += -DSC_ANDROID
# ajs 20091229: the line below reeks of unintended consequences
LOCAL_CFLAGS    += -D__GCC__
LOCAL_SRC_FILES := \
    Source/server//Rendezvous.cpp \
    Source/server//Samp.cpp \
    Source/server//SC_BufGen.cpp \
    Source/server//SC_Carbon.cpp \
    Source/server//SC_ComPort.cpp \
    Source/server//SC_Complex.cpp \
    Source/server//SC_CoreAudio.cpp \
    Source/server//SC_Dimension.cpp \
    Source/server//SC_Errors.cpp \
    Source/server//SC_Graph.cpp \
    Source/server//SC_GraphDef.cpp \
    Source/server//SC_Group.cpp \
    Source/server//SC_Lib_Cintf.cpp \
    Source/server//SC_Lib.cpp \
    Source/server//SC_MiscCmds.cpp \
    Source/server//SC_Node.cpp \
    Source/server//SC_Rate.cpp \
    Source/server//SC_SequencedCommand.cpp \
    Source/server//SC_Str4.cpp \
    Source/server//SC_SyncCondition.cpp \
    Source/server//scsynth_main.cpp \
    Source/server//SC_Unit.cpp \
    Source/server//SC_UnitDef.cpp \
    Source/server//SC_World.cpp \
    Source/common//SC_Sem.cpp \
    Source/common//SC_DirUtils.cpp \
    Source/common//SC_StringParser.cpp \
    Source/common//SC_AllocPool.cpp \
    Source/libc/glob.c 

include $(BUILD_SHARED_LIBRARY)
