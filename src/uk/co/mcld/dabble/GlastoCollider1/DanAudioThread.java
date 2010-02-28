package uk.co.mcld.dabble.GlastoCollider1;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import java.io.File;
import android.os.Environment;

class DanAudioThread extends Thread {

	GlastoCollider1 theApp;
	
	final int numOutChans = 2; // note, don't change this without changing the AudioTrack's mono-or-stereo config
	final int bytesPerSample = 2; // note, don't change this without changing the AudioTrack's 16bit config, OH and all the NDK stuff
	
	final int bufSizeFrames = 64 * 16; // what's a good choice on an android device? 
	// on my tattoo 600 is smallest so (64*8=512) not enough, on the vm even 64*16 not enough!
	final int bufSizeBytes = bufSizeFrames * numOutChans * bytesPerSample;
	int sampleRateInHz = 11025; //44100;

	byte[] audioBuf = new byte[bufSizeBytes];
	AudioTrack audioTrack;
	
	boolean running=true; // user-settable
	boolean ended=false; // whether the audio thread really has stopped and tidied up or not

	// load and declare the NDK C++ methods
    static { System.loadLibrary("scsynth"); }
	public native void scsynth_android_initlogging();
	public native int  scsynth_android_start(int srate, int hwBufSize, int numOutChans, String pluginsPath, String synthDefsPath);
	public native int  scsynth_android_genaudio(byte[] someAudioBuf);
	public native void scsynth_android_makeSynth(String synthName);
	public native void scsynth_android_quit();
    
	public DanAudioThread(GlastoCollider1 theApp){
		this.theApp = theApp;
		Log.i("GlastoCollider1", "DanAudioThread - about to invoke native scsynth_android_initlogging()");
		scsynth_android_initlogging();
		
		// tell scsynth where we're expecting synthdefs
		File dataDir = Environment.getDataDirectory();
		String dataDirStr = dataDir.toString();
		Log.i("GlastoCollider1", "DanAudioThread - data dir is " + dataDirStr);
		
		int result = scsynth_android_start(sampleRateInHz, bufSizeFrames, numOutChans, dataDirStr, dataDirStr);
		Log.i("GlastoCollider1", "DanAudioThread - result of scsynth_android_start() is " + result);
	}

	public void setRunning(boolean val){
		running = val;
	}
	public boolean isRunning(){
		return running;
	}
	public boolean isEnded(){
		return ended;
	}
	
	public void run(){
		int minSize = AudioTrack.getMinBufferSize(sampleRateInHz, AudioFormat.CHANNEL_CONFIGURATION_STEREO, AudioFormat.ENCODING_PCM_16BIT);
		if(minSize > bufSizeBytes){
			System.err.println("AudioTrack.getMinBufferSize " + minSize + " too large for configured buffer " + bufSizeBytes);
		}
		
		setPriority(Thread.MAX_PRIORITY);
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		// instantiate AudioTrack
		try{
			audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
					AudioFormat.ENCODING_PCM_16BIT, bufSizeBytes, AudioTrack.MODE_STREAM);
		}catch(IllegalArgumentException e){
			System.err.println("DANDROID failed to create AudioTrack object");
			e.printStackTrace();
		}

		// hackily set up some dummy audio in the buffer
		for(int i=0; i<audioBuf.length; i++){
			audioBuf[i] = (byte)((i * 5) % 256);
		}

		audioTrack.play(); // this must be done BEFORE we write data to it
		
		scsynth_android_makeSynth("default");
		
		//for(int i=0; i< 100; i++){
		int ndkReturnVal;
		while(running){
			// let the NDK make the sound!
			ndkReturnVal = scsynth_android_genaudio(audioBuf);
			audioTrack.write(audioBuf, 0, audioBuf.length);
			Thread.yield();
		}

		// TODO: tell scsynth to stop, then let *it* call back to stop the audio running
		audioTrack.stop();
		ended = true;
	}
}
