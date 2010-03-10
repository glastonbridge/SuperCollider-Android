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
#include "SC_HiddenWorld.h"
#include "SC_CoreAudio.h"  // for SC_AndroidJNIAudioDriver

#include <dirent.h>  // ONLY for the debug folder scanning

static const char * MY_JAVA_CLASS = "uk/co/mcld/dabble/GlastoCollider1/SCAudio"; //TODO sth more generic

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

extern "C" int scsynth_android_start(JNIEnv* env, jobject obj, 
						jint srate, jint hwBufSize, jint numOutChans,
						jstring pluginsPath, jstring synthDefsPath){

	jboolean isCopy;
	const char* pluginsPath_c   = env->GetStringUTFChars(pluginsPath,   &isCopy);
	const char* synthDefsPath_c = env->GetStringUTFChars(synthDefsPath, &isCopy);
	__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "scsynth_android_start(%i, %i, %i, %s, %s)",
		(int)srate, (int)hwBufSize, (int)numOutChans, pluginsPath_c, synthDefsPath_c);
    setenv("SC_PLUGIN_PATH",   pluginsPath_c,   1);
    setenv("SC_SYNTHDEF_PATH", synthDefsPath_c, 1);
    
    // DEBUG: Check that we can read the path
	DIR *dip;
	struct dirent *dit;
	if ((dip = opendir(pluginsPath_c)) == NULL){
		__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "Could not opendir(%s)\n", pluginsPath_c);
	}else{
		__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "OK, listing opendir(%s)\n", pluginsPath_c);
		while ((dit = readdir(dip)) != NULL){
			__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "Entry: %s\n", dit->d_name);
		}
		if (closedir(dip) == -1)
			__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "Could not closedir(%s)\n", pluginsPath_c);
	}

	env->ReleaseStringUTFChars(pluginsPath,   pluginsPath_c);
	env->ReleaseStringUTFChars(synthDefsPath, synthDefsPath_c);



	WorldOptions options = kDefaultWorldOptions;
	options.mPreferredSampleRate = srate;
	options.mPreferredHardwareBufferFrameSize = hwBufSize;
	options.mNumOutputBusChannels = numOutChans;
	options.mNumInputBusChannels  = 0;
	
	// Reduce things down a bit for lower-spec - these are all open for review
	options.mNumBuffers  = 512;
	options.mMaxGraphDefs = 512;
	options.mMaxWireBufs = 512;
	options.mNumAudioBusChannels = 32;
	options.mRealTimeMemorySize = 512;
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

/**
* The callback that java uses to ask scsynth for some sound.
* The length of the array is not necessarily the same as sc's block size -
* it should be an exact multiple of it though.
*/
JNIEXPORT jint JNICALL scsynth_android_genaudio ( JNIEnv* env, jobject obj, jbyteArray arr )
{
	jbyte *carr;
	jint i, posi, posf, len;
	carr = (env)->GetByteArrayElements(arr, NULL);
	if(carr == NULL){
		return 1;
	}
	len = (env)->GetArrayLength(arr);
	
	// android audio buffers are fixed as 16-bit, so we shrink by factor of 2:
	jint numSamples = len / 2;
	int* arri = (int*) carr;
	
	// NB numSamples genuinely is num samples (not num frames as sometimes in sc code)
	
	((SC_AndroidJNIAudioDriver*)AudioDriver(world))->genaudio(arri, numSamples);
	
	env->ReleaseByteArrayElements(arr, carr, 0);
	return 0;
}

extern "C" void scsynth_android_makeSynth(JNIEnv* env, jobject obj, jstring theName){
    if (world->mRunning){
    	jboolean isCopy;
    	const char* synthName = env->GetStringUTFChars(theName, &isCopy);
    	scprintf("scsynth_android_makeSynth(%s)\n", synthName);
        OSCMessages messages;
        small_scpacket packet;
        size_t messageSize =  messages.createSynthMessage(&packet, synthName);
        World_SendPacket(world,messageSize,  (char*)packet.buf, null_reply_func);
        env->ReleaseStringUTFChars(theName, synthName);
    }else{
    	scprintf("scsynth_android_makeSynth: not running!\n");
    }
}

extern "C" void scsynth_android_quit(JNIEnv* env, jobject obj){
    OSCMessages messages;
    if (world && world->mRunning){
         small_scpacket packet = messages.quitMessage();
         World_SendPacket(world, 8,(char*)packet.buf, null_reply_func);
    }else{
    	scprintf("scsynth_android_quit: not running!\n");
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
	cls = env->FindClass(MY_JAVA_CLASS); 
	if (cls == NULL) {
		__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "JNI_Onload FindClass failed");
		return JNI_ERR;
	}
	static JNINativeMethod methods[] = {
		// name, signature, function pointer
		{ "scsynth_android_initlogging", "()V",   (void *) &scsynth_android_initlogging },
		{ "scsynth_android_start"      , "(IIILjava/lang/String;Ljava/lang/String;)I",   (void *) &scsynth_android_start       },
		{ "scsynth_android_genaudio"   , "([B)I", (void *) &scsynth_android_genaudio    },
		{ "scsynth_android_makeSynth"  , "(Ljava/lang/String;)V",   (void *) &scsynth_android_makeSynth   },
		{ "scsynth_android_quit"       , "()V",   (void *) &scsynth_android_quit        },
	};
	env->RegisterNatives(cls, methods, sizeof(methods)/sizeof(methods[0]) );

	return JNI_VERSION_1_4;
}
