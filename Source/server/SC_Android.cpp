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
						jint srate, jint hwBufSize, jint numOutChans){

	__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "scsynth_android_start(%i, %i, %i)",
		(int)srate, (int)hwBufSize, (int)numOutChans);

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
	
	
	
	
/*	
	int bufFrames = world->mBufLength;
	// TODO: efficiency
	jint numBufs = numSamples / bufFrames;
	// TODO: assert(numBufs * bufFrames == leni); // exact divisor
	posi = 0;

	float *inBuses = world->mAudioBus + world->mNumOutputs * bufFrames;
	float *outBuses = world->mAudioBus;
	int32 *inTouched = world->mAudioBusTouched + world->mNumOutputs;
	int32 *outTouched = world->mAudioBusTouched;

	int minInputs = world->mNumInputs;
	int minOutputs = world->mNumOutputs;

	int bufFramePos = 0;

	int64 oscTime = mOSCbuftime;
	int64 oscInc = mOSCincrement;
	double oscToSamples = mOSCtoSamples;

	// main loop copied from the PortAudio driver
	for (int i = 0; i < numBufs; ++i, world->mBufCounter++, bufFramePos += bufFrames)
	{
		int32 bufCounter = world->mBufCounter;
		int32 *tch;

		// copy+touch inputs
		tch = inTouched;
		for (int k = 0; k < minInputs; ++k)
		{
			const float *src = inBuffers[k] + bufFramePos;
			float *dst = inBuses + k * bufFrames;
			for (int n = 0; n < bufFrames; ++n) *dst++ = *src++;
			*tch++ = bufCounter;
		}

		// run engine
		int64 schedTime;
		int64 nextTime = oscTime + oscInc;
		while ((schedTime = mScheduler.NextTime()) <= nextTime) {
			float diffTime = (float)(schedTime - oscTime) * oscToSamples + 0.5;
			float diffTimeFloor = floor(diffTime);
			world->mSampleOffset = (int)diffTimeFloor;
			world->mSubsampleOffset = diffTime - diffTimeFloor;

			if (world->mSampleOffset < 0) world->mSampleOffset = 0;
			else if (world->mSampleOffset >= world->mBufLength) world->mSampleOffset = world->mBufLength-1;

			SC_ScheduledEvent event = mScheduler.Remove();
			event.Perform();
		}
		world->mSampleOffset = 0;
		world->mSubsampleOffset = 0.f;

		World_Run(world);

		// copy touched outputs
		tch = outTouched;
		for (int k = 0; k < minOutputs; ++k) {
			float *dst = outBuffers[k] + bufFramePos;
			if (*tch++ == bufCounter) {
				float *src = outBuses + k * bufFrames;
				for (int n = 0; n < bufFrames; ++n) *dst++ = *src++;
			} else {
				for (int n = 0; n < bufFrames; ++n) *dst++ = 0.0f;
			}
		}

		// update buffer time
		oscTime = mOSCbuftime = nextTime;
	}




	for(i=0; i<numBufs; ++i){
		// fill audioData[]
		
//NOT DONE
//NOT DONE
//NOT DONE
		
		// drop sound into carr[numBlocks * i]
		posf=0;
		arri[posi++] = (int)(audioData[posf++]);
	}
*/
	
	/*
	for(i=0; i<len; ++i){
		// TODO some decent audio...
		carr[i] = (i * 3) % 256;
	}
	*/
	
	env->ReleaseByteArrayElements(arr, carr, 0);
	return 0;
}


extern "C" void scsynth_android_makeSynth(JNIEnv* env, jobject obj, jstring theName){
    if (world->mRunning){
    	jboolean isCopy;
    	const char* synthName = env->GetStringUTFChars(theName, &isCopy);
        OSCMessages messages;
        small_scpacket packet;
        size_t messageSize =  messages.createSynthMessage(&packet, synthName);
        World_SendPacket(world,messageSize,  (char*)packet.buf, null_reply_func);
        env->ReleaseStringUTFChars(theName, synthName);
    }
}

extern "C" void scsynth_android_quit(JNIEnv* env, jobject obj){
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
		{ "scsynth_android_initlogging", "()V",   (void *) &scsynth_android_initlogging },
		{ "scsynth_android_start"      , "(III)I",   (void *) &scsynth_android_start       },
		{ "scsynth_android_genaudio"   , "([B)I", (void *) &scsynth_android_genaudio    },
		{ "scsynth_android_makeSynth"  , "(Ljava/lang/String;)V",   (void *) &scsynth_android_makeSynth   },
		{ "scsynth_android_quit"       , "()V",   (void *) &scsynth_android_quit        },
	};
	env->RegisterNatives(cls, methods, sizeof(methods)/sizeof(methods[0]) );

	return JNI_VERSION_1_4;
}
