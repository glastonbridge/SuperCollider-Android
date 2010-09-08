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
#include "sc_msg_iter.h"
#include "SC_HiddenWorld.h"
#include "SC_ComPort.h"
#include "SC_CoreAudio.h"  // for SC_AndroidJNIAudioDriver

#include <dirent.h>  // ONLY for the debug folder scanning

#include <queue>

static const char * MY_JAVA_CLASS = "net/sf/supercollider/android/SCAudio";
static const char * OSC_MESSAGE_CLASS = "net/sf/supercollider/android/OscMessage";

// For use when passing messages back from scsynth
static std::queue<std::string> scsynthMessages;
static const int messageQueueMaxLength = 10;

void scvprintf_android(const char *fmt, va_list ap){
	// note, currently no way to choose log level of scsynth messages so all set as 'debug'
	__android_log_vprint(ANDROID_LOG_DEBUG, "libscsynth", fmt, ap);
}

extern "C" void scsynth_android_initlogging() {
	SetPrintFunc((PrintFunc) *scvprintf_android);
	#ifndef NDEBUG
		scprintf("SCSYNTH->ANDROID logging active (no debug)\n");
	#else
		scprintf("SCSYNTH->ANDROID logging active (debug)\n");
	#endif
}

void* scThreadFunc(void* arg)
{
    World* world  = (World*)arg;
    World_WaitForQuit(world);
    return 0;
}

void null_reply_func(struct ReplyAddress* /*addr*/, char* /*msg*/, int /*size*/);

// For now, add everything to a fifo.  I first thought to do this as a callback,
// but calling Java from an arbitrary pthread is a no-no
void androidReplyFunc(struct ReplyAddress* /*addr*/, char* inData, int inSize) {
    scsynthMessages.push(std::string(inData,inSize));
    if(scsynthMessages.size()>messageQueueMaxLength) scsynthMessages.pop();
}

jobject convertMessageToJava(JNIEnv* myEnv, char* inData, int inSize) {

    jclass oscMessageClass = myEnv->FindClass(OSC_MESSAGE_CLASS);

	if (!oscMessageClass) {
		scprintf("convertMessageToJava could not find the JAVA OSC representation");
		return NULL;
	}

	if (inSize<=0 || !inData) {
		return NULL;
	}

	jmethodID oscConstructor = myEnv->GetMethodID(oscMessageClass, "<init>", "()V");
	if (!oscConstructor) {
		scprintf("convertMessageToJava could not find a constructor for the JAVA OSC representation");
		return NULL;
	}

	jobject oscObject = myEnv->NewObject(oscMessageClass, oscConstructor);
	jmethodID addInt = myEnv->GetMethodID(oscMessageClass, "add", "(I)Z");
	jmethodID addStr = myEnv->GetMethodID(oscMessageClass, "add", "(Ljava/lang/String;)Z");
	jmethodID addFlt = myEnv->GetMethodID(oscMessageClass, "add", "(F)Z");
	jmethodID addLng = myEnv->GetMethodID(oscMessageClass, "add", "(J)Z");

	// Did I steal this wholesale from dumpOSCmsg?  Yes I did.  -ajs 20100826
	char * data;
	int size;
	if (inData[0]) {
		char *addr = inData;
		data = OSCstrskip(inData);
		size = inSize - (data - inData);
		jstring jaddr = myEnv->NewStringUTF(addr);
		myEnv->CallBooleanMethod(oscObject,addStr,jaddr);
	}
	else
	{
		myEnv->CallBooleanMethod(oscObject,addInt,OSCint(inData));
		data = inData + 4;
		size = inSize - 4;
	}

	sc_msg_iter msg(size, data);

	bool ok(true);
	while (msg.remain() && ok)
	{
		char c = msg.nextTag('i');
		jstring jstr;
		switch(c)
		{
			case 'i' :
				myEnv->CallBooleanMethod(oscObject,addInt,msg.geti());
				break;
			case 'f' :
				myEnv->CallBooleanMethod(oscObject,addFlt,msg.getf());
				break;
			case 's' :
				jstr = myEnv->NewStringUTF(msg.gets());
				myEnv->CallBooleanMethod(oscObject,addStr,jstr);
				break;
			default :
				scprintf("convertMessageToJava unknown/unimplemented tag '%c' 0x%02x", isprint(c)?c:'?', (unsigned char)c & 255);
				ok = false;
			break;
		}
	}
	return oscObject;
}

JNIEXPORT jboolean JNICALL scsynth_android_hasMessages ( JNIEnv* env, jobject obj )
{
	return !scsynthMessages.empty();
}

JNIEXPORT jobject JNICALL scsynth_android_getMessage ( JNIEnv* env, jobject obj )
{
	if (!scsynthMessages.empty()) {
		std::string firstMessage (scsynthMessages.front());
		scsynthMessages.pop();
		char* data = new char[firstMessage.length()+1];
		int length (firstMessage.copy(data,firstMessage.length()));
		data[length] = 0;
		jobject oscMessage (convertMessageToJava(env, data, length));
		delete[] data;
		return oscMessage;
	}
	return NULL;
}



static World * world;

// buffer used for shoogling data up into java
static short* buff;
static int bufflen;

static SC_UdpInPort* udpInPort = NULL;
/*
 * this is like World_OpenUDP() except it stores a static reference to the object
 * TODO: maybe we could destatickify by pass a reference to the object back to java, to be passed to the closer?
 */
extern "C" void scsynth_android_open_udp(JNIEnv* env, jobject obj, jint port) {
	scprintf("scsynth_android_open_udp\n");
	try {
		udpInPort = new SC_UdpInPort(world, port);
	} catch (std::exception& exc) {
		scprintf("Exception in scsynth_android_open_udp: %s\n", exc.what());
		udpInPort = NULL;
	} catch (...) {
	}
}
extern "C" void scsynth_android_close_udp(JNIEnv* env, jobject obj) {
	scprintf("scsynth_android_close_udp\n");
	if(udpInPort==NULL){
		scprintf("scsynth_android_close_udp : no open port to close\n");
		return;
	}
	try {
		udpInPort->~SC_UdpInPort();
		udpInPort = NULL;
	} catch (std::exception& exc) {
		scprintf("Exception in scsynth_android_close_udp: %s\n", exc.what());
	} catch (...) {
	}
}

extern "C" int scsynth_android_start(JNIEnv* env, jobject obj, 
						jint srate, jint hwBufSize, jint numInChans, jint numOutChans, jint shortsPerSample,
						jstring pluginsPath, jstring synthDefsPath){

	jboolean isCopy;
	bufflen = shortsPerSample*numOutChans*hwBufSize;
	buff = (short*) calloc(bufflen,sizeof(short));
	const char* pluginsPath_c   = env->GetStringUTFChars(pluginsPath,   &isCopy);
	const char* synthDefsPath_c = env->GetStringUTFChars(synthDefsPath, &isCopy);
	__android_log_print(ANDROID_LOG_DEBUG, "libscsynth", "scsynth_android_start(%i, %i, %i, %i, %i, %s, %s)",
		(int)srate, (int)hwBufSize, (int)numInChans, (int)numOutChans, (int)shortsPerSample, pluginsPath_c, synthDefsPath_c);
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
	options.mNumInputBusChannels  = numInChans;
	options.mNumOutputBusChannels = numOutChans;
	
	// Reduce things down a bit for lower-spec - these are all open for review
	options.mNumBuffers  = 512;
	options.mMaxGraphDefs = 512;
	options.mMaxWireBufs = 512;
	options.mNumAudioBusChannels = 32;
	options.mRealTimeMemorySize = 512;
	options.mNumRGens = 16;
	options.mLoadGraphDefs = 1;
	options.mVerbosity = 2; // TODO: reduce this back to zero for non-debug builds once dev't is stable
	options.mBufLength = 64; // was hwBufSize / numOutChans;
	
	
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
JNIEXPORT jint JNICALL scsynth_android_genaudio ( JNIEnv* env, jobject obj, jshortArray arr )
{
	env->GetShortArrayRegion(arr, 0, bufflen, buff);

	((SC_AndroidJNIAudioDriver*)AudioDriver(world))->genaudio(buff, bufflen);

	env->SetShortArrayRegion(arr, 0, bufflen, buff);
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

/**
 * Currently we assume that a message is either
 * 1. A primitive (strings, ints, etc) message
 * 2. A bundle of other messages
 * There is no provision for the situation where a message contains both
 * primitive and bundle data, but you can bundle bundles
 */
void makePacket(JNIEnv* env, jobjectArray oscMessage, small_scpacket& packet,int start=0) {
    int numElements = env->GetArrayLength(oscMessage);

#ifndef NDEBUG
    scprintf("received a message with %i elements\n",numElements);
#endif
    if (numElements<=0) return; // No message
    int i;
    jobject obj;
    const char* stringPtr;
    jclass integerClass = env->FindClass("java/lang/Integer");
    jmethodID getIntMethod = env->GetMethodID(integerClass,"intValue","()I");
    jclass floatClass = env->FindClass("java/lang/Float");
    jmethodID getFloatMethod = env->GetMethodID(floatClass,"floatValue","()F");
    jclass stringClass = env->FindClass("java/lang/String");
    jclass oscClass = env->FindClass(OSC_MESSAGE_CLASS);
    jmethodID toArrayMethod = env->GetMethodID(oscClass,"toArray","()[Ljava/lang/Object;");
	obj = env->GetObjectArrayElement(oscMessage,0);
	if (env->IsInstanceOf(obj,oscClass)) {
#ifndef NDEBUG
		scprintf("it's a bundle!\n");
#endif
		packet.OpenBundle(0);
		while (start<numElements) {
			obj = env->GetObjectArrayElement(oscMessage,start);
			jobjectArray bundle = (jobjectArray) env->CallObjectMethod(obj,toArrayMethod);
#ifndef NDEBUG
			scprintf("making a new packet %i\n",start);
#endif
			packet.BeginMsg();
			makePacket(env, bundle, packet);
			packet.EndMsg();
			++start;
		}
		packet.CloseBundle();
	} else if (env->IsInstanceOf(obj,stringClass)) {
		stringPtr = env->GetStringUTFChars((jstring)obj,NULL);
		packet.adds(stringPtr);
#ifndef NDEBUG
		scprintf("cmd %s\n",stringPtr);
#endif
		env->ReleaseStringUTFChars((jstring)obj,stringPtr);
		packet.maketags(numElements);
		packet.addtag(',');
		for (i=1;i<numElements;++i) {
			obj = env->GetObjectArrayElement(oscMessage,i);
			if (env->IsInstanceOf(obj,integerClass)) {
				packet.addtag('i');
				packet.addi(env->CallIntMethod(obj,getIntMethod));
			} else if (env->IsInstanceOf(obj,floatClass)) {
				packet.addtag('f');
				packet.addf(env->CallFloatMethod(obj,getFloatMethod));
			} else if (env->IsInstanceOf(obj,stringClass)) {
				packet.addtag('s');
				stringPtr = env->GetStringUTFChars((jstring)obj,NULL);
#ifndef NDEBUG
				scprintf("arg %s\n",stringPtr);
#endif
				packet.adds(stringPtr);
				env->ReleaseStringUTFChars((jstring)obj,stringPtr);
			}
		}
    }
}

extern "C" void scsynth_android_doOsc(JNIEnv* env, jobject classobj, jobjectArray oscMessage){
    if (world->mRunning){
        small_scpacket packet;
        makePacket(env,oscMessage,packet);
        World_SendPacket(world, packet.size(),  (char*)packet.buf, androidReplyFunc);
    }else{
    	scprintf("scsynth_android_doOsc: not running!\n");
    }
}

/** The main thing JNI_OnLoad does is register the functions so that JNI knows
* how to invoke them. It's not necessary on Android but we'd have to use
* horrible qualified function names otherwise. */
extern "C" jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved){
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
		{ "scsynth_android_open_udp"   , "(I)V",  (void *) &scsynth_android_open_udp       },
		{ "scsynth_android_close_udp"  , "()V",   (void *) &scsynth_android_close_udp      },
		{ "scsynth_android_start"      , "(IIIIILjava/lang/String;Ljava/lang/String;)I",   (void *) &scsynth_android_start    },
		{ "scsynth_android_genaudio"   , "([S)I", (void *) &scsynth_android_genaudio    },
		{ "scsynth_android_makeSynth"  , "(Ljava/lang/String;)V",   (void *) &scsynth_android_makeSynth   },
		{ "scsynth_android_doOsc"      , "([Ljava/lang/Object;)V", (void *) &scsynth_android_doOsc },
		{ "scsynth_android_hasMessages", "()Z", (void *) &scsynth_android_hasMessages },
		{ "scsynth_android_getMessage" , "()Lnet/sf/supercollider/android/OscMessage;", (void *) &scsynth_android_getMessage },
	};
	env->RegisterNatives(cls, methods, sizeof(methods)/sizeof(methods[0]) );
	return JNI_VERSION_1_4;
}
