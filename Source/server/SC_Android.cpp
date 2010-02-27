/*
	SuperCollider Android wrapper code
    Copyright (c) 2010 Dan Stowell. All rights reserved.
    Includes some code from SuperColliderAU by Gerard Roma.

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/


#include "SC_World.h"
#include "SC_WorldOptions.h"

#include <android/log.h>
#include <jni.h>
#include "OSCMessages.h"

void scvprintf_android(const char *fmt, va_list ap){
	// note, currently no way to choose log level of scsynth messages so all set as 'debug'
	//  #ifndef NDEBUG
	__android_log_vprint(ANDROID_LOG_DEBUG, "libscsynth", fmt, ap);
	//  #endif
}

extern "C" void scsynth_android_initlogging() {
	SetPrintFunc((PrintFunc) *scvprintf_android);
	scprintf("SCSYNTH->ANDROID logging active\n");
}

void* scThreadFunc(void* arg)
{
    World* world  = (World*)arg;
    World_WaitForQuit(world);
    return 0;
}
void null_reply_func(struct ReplyAddress* /*addr*/, char* /*msg*/, int /*size*/);


static World * world;

extern "C" int scsynth_android_start(){
	WorldOptions options = kDefaultWorldOptions;
	// Reduce things down a bit for lower-spec - these are all open for review
	options.mNumBuffers  = 512;
	options.mMaxGraphDefs = 512;
	options.mMaxWireBufs = 512;
	options.mNumAudioBusChannels = 32;
	options.mNumInputBusChannels  = 2;
	options.mNumOutputBusChannels = 2;
	options.mRealTimeMemorySize = 512;
	options.mPreferredSampleRate = 22050;
	options.mNumRGens = 16;
	options.mLoadGraphDefs = 1; // TODO: decide whether to load from folders or directly
	options.mVerbosity = 2; // TODO: reduce this back to zero for non-debug builds once dev't is stable
	
	// Similar to SCProcess:startup :
	pthread_t scThread;
	OSCMessages messages;

    world = World_New(&options);
    //world->mDumpOSC=2;
    if (world) {
        pthread_create (&scThread, NULL, scThreadFunc, (void*)world);
    }
    if (world->mRunning){
        small_scpacket packet = messages.initTreeMessage();
        World_SendPacket(world, 16, (char*)packet.buf, null_reply_func);
        return 0;
    }else{
    	return 1;
    }
}

extern "C" void scsynth_android_makeSynth(const char* synthName){
    if (world->mRunning){
        OSCMessages messages;
        small_scpacket packet;
        size_t messageSize =  messages.createSynthMessage(&packet, synthName);
        World_SendPacket(world,messageSize,  (char*)packet.buf, null_reply_func);
    }
}

extern "C" void scsynth_android_quit(){
    OSCMessages messages;
    if (world && world->mRunning){
         small_scpacket packet = messages.quitMessage();
         World_SendPacket(world, 8,(char*)packet.buf, null_reply_func);
    }
}

/** The main thing JNI_OnLoad does is register the functions so that JNI knows
* how to invoke them. It's not necessary on Android but we'd have to use
* horrible qualified function names otherwise. */
extern "C" jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved){

	// btw storing JavaVM *vm in a static variable is very common. i don't need it so far yet.
	JNIEnv* env;
	if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK)
		return -1;

	jclass  cls;
	cls = env->FindClass("uk/co/mcld/dabble/GlastoCollider1/DanAudioThread"); //TODO sth more generic
	if (cls == NULL) {
		__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "JNI_Onload FindClass failed");
		return JNI_ERR;
	}
	static JNINativeMethod methods[] = {
		// name, signature, function pointer
		{ "scsynth_android_initlogging", "()V", (void *) &scsynth_android_initlogging },
		{ "scsynth_android_start"      , "()I", (void *) &scsynth_android_start       },
		{ "scsynth_android_makeSynth"  , "()V", (void *) &scsynth_android_makeSynth   },
		{ "scsynth_android_quit"       , "()V", (void *) &scsynth_android_quit        },
	};
	env->RegisterNatives(cls, methods, sizeof(methods)/sizeof(methods[0]) );

	return JNI_VERSION_1_4;
}
