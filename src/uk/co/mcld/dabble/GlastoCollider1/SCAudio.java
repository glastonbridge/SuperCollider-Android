package uk.co.mcld.dabble.GlastoCollider1;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;
import java.io.File;

/**
 * Responsible for the event loop which drives SuperCollider
 * under the hood of the NDK.
 * 
 * @author Dan Stowell
 *
 */
class SCAudio extends Thread {
	private static final String TAG="GlastoCollider1";
	SuperColliderActivity theApp;
	
	/**
	 * SUPERCOLLIDER AUDIO OUTPUT SETTINGS
	 * 
	 * Fairly lo-fi by default, there's still room for optimisation.  A good acceptance
	 * test is to waggle the notifications bar about while running - does it glitch much?
	 */
	final int numOutChans = 1; 
	final int shortsPerSample = 1; 
	final int bufSizeFrames = 64*8 ;  
	final int bufSizeShorts = bufSizeFrames * numOutChans * shortsPerSample; 
	int sampleRateInHz = 11025;

	short[] audioBuf = new short[bufSizeShorts];
	AudioTrack audioTrack;
	
	boolean running=true; // user-settable
	boolean ended=false; // whether the audio thread really has stopped and tidied up or not

	// load and declare the NDK C++ methods
    static { System.loadLibrary("scsynth"); }
	public native void scsynth_android_initlogging();
	public native int  scsynth_android_start(int srate, int hwBufSize, int numOutChans, int shortsPerSample, String pluginsPath, String synthDefsPath);
	public native int  scsynth_android_genaudio(short[] someAudioBuf);
	public native void scsynth_android_makeSynth(String synthName);
	public native void scsynth_android_quit();
    
	public SCAudio(SuperColliderActivity theApp){
		this.theApp = theApp;
		Log.i(TAG, "SCAudio - about to invoke native scsynth_android_initlogging()");
		scsynth_android_initlogging();
		String scDirStr = "/sdcard/supercollider";
		String dataDirStr = scDirStr+"/synthdefs";;
		File dataDir = new File(dataDirStr);
		if(dataDir.mkdirs()) {  
		} else if (!dataDir.isDirectory()) {
			Log.e(TAG,"Could not create directory "+dataDirStr);
		}
		String dllDirStr = "/data/data/uk.co.mcld.dabble.GlastoCollider1/lib"; // TODO: not very extensible, hard coded, generally sucks

		Log.i(TAG, "SCAudio - data dir is " + dataDirStr);
		int result = 0xdead;
		try {
			result = scsynth_android_start(sampleRateInHz, bufSizeFrames, numOutChans, shortsPerSample, dllDirStr, dataDirStr);
		} catch (Exception e) {
			e.printStackTrace();
		}
				
		Log.i(TAG, "SCAudio - result of scsynth_android_start() is " + result);
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
		int minSize = AudioTrack.getMinBufferSize(
				sampleRateInHz, 
				numOutChans==2?
						AudioFormat.CHANNEL_CONFIGURATION_STEREO
						:AudioFormat.CHANNEL_CONFIGURATION_MONO, 
				AudioFormat.ENCODING_PCM_16BIT);
		
		setPriority(Thread.MAX_PRIORITY);
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		// instantiate AudioTrack
		try{
			audioTrack = new AudioTrack(
					AudioManager.STREAM_MUSIC, 
					sampleRateInHz, 
					numOutChans==2?
							AudioFormat.CHANNEL_CONFIGURATION_STEREO
							:AudioFormat.CHANNEL_CONFIGURATION_MONO, 
					AudioFormat.ENCODING_PCM_16BIT, 
					minSize, 
					AudioTrack.MODE_STREAM);
		}catch(IllegalArgumentException e){
			System.err.println("DANDROID failed to create AudioTrack object");
			e.printStackTrace();
		}

		audioTrack.play(); // this must be done BEFORE we write data to it
		
		scsynth_android_makeSynth("default");
		
		//for(int i=0; i< 100; i++){
		int ndkReturnVal;
		while(running){
			// let the NDK make the sound!
			ndkReturnVal = scsynth_android_genaudio(audioBuf);
			if(ndkReturnVal!=0) {
				Log.e(TAG,"SCSynth returned non-zero value "+ndkReturnVal);
				running=false;
			}
			audioTrack.write(audioBuf, 0, bufSizeShorts);
			Thread.yield();
		}

		// TODO: tell scsynth to stop, then let *it* call back to stop the audio running
		audioTrack.stop();
		audioTrack.release();
		ended = true;
	}
}
