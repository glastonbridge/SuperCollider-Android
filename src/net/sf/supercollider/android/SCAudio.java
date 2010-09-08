package net.sf.supercollider.android;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * Responsible for the event loop which drives SuperCollider
 * under the hood of the NDK.
 * 
 * If linking against SuperCollider as a library, use this class
 * 
 * @author Dan Stowell
 *
 */
public class SCAudio extends Thread {
	protected static final String TAG="SuperCollider-Android";
	
	/**
	 * SUPERCOLLIDER AUDIO OUTPUT SETTINGS
	 * 
	 * Fairly lo-fi by default, there's still room for optimisation.  A good acceptance
	 * test is to waggle the notifications bar about while running - does it glitch much?
	 */
	private int numInChans = 1; // can choose in ctor but not afterwards 
	final int numOutChans = 1;
	public static int sampleRateInHz = 22050;
	// bufSizeFrames (size of audio buffer passed in from android) 
	//  must be a multiple of 64 since scsynth's internal block length is unchanged from its default of 64.
	// 64*16 was OK for 11kHz.
	final int bufSizeFrames = 64*8;
	final int shortsPerSample = 1; // this is tied to what the NDK code does to pass audio to scsynth, can't change easily.
	final int bufSizeShorts = bufSizeFrames * numOutChans * shortsPerSample; 

	short[] audioBuf = new short[bufSizeShorts];
	AudioRecord audioRecord; // input
	AudioTrack audioTrack;   // output
	
	/*
	 * Audio driver state variables.
	 * The cycle looks like this:
	 *   (1) before run() is invoked,     running==false and ended==true
	 *   (2) when run() is invoked,       running==true  and ended==false  <-- audio driver main loop
	 *   (3) when sendQuit() is invoked,  running==false and ended==false  <-- briefly, waiting for SC to tell us [/done /quit]
	 *   (4) when SC properly shuts down, running==false and ended==true
	 */
	private boolean running=false; // set to true when run() is invoked, set to false by ScService.stop() (as well as sending a /quit msg)
	private boolean ended=true; // whether the audio thread really has stopped and tidied up or not

	// load and declare the NDK C++ methods
	// Also load dependencies, as the native dlopen won't look in app directories
    static { 
    	System.loadLibrary("sndfile");
    	System.loadLibrary("scsynth"); 
    }
	public static native void scsynth_android_initlogging();
	public static native int  scsynth_android_start(int srate, int hwBufSize, int numInChans, int numOutChans, int shortsPerSample, String pluginsPath, String synthDefsPath);
	public static native void scsynth_android_open_udp(int port);
	public static native void scsynth_android_close_udp();
	public static native int  scsynth_android_genaudio(short[] someAudioBuf);
	public static native void scsynth_android_makeSynth(String synthName);
	public static native void scsynth_android_doOsc(Object[] message);
	public static native boolean scsynth_android_hasMessages();
	public static native OscMessage scsynth_android_getMessage();
	public static native void scsynth_android_quit();
    
	public SCAudio(String dllDirStr){
		this(1,dllDirStr);
	}
	public SCAudio(int numInChans,String dllDirStr){
		this.numInChans = numInChans;
		Log.i(TAG, "SCAudio - about to invoke native scsynth_android_initlogging()");
		scsynth_android_initlogging();

		Log.i(TAG, "SCAudio - data dir is " + ScService.dataDirStr);
		int result = 0xdead;
		try {
			result = scsynth_android_start(sampleRateInHz, bufSizeFrames, numInChans, numOutChans, shortsPerSample, 
					dllDirStr, ScService.dataDirStr);
		} catch (Exception e) {
			e.printStackTrace();
		}
				
		Log.i(TAG, "SCAudio - result of scsynth_android_start() is " + result);
	}
	
	public void sendMessage(OscMessage oscMessage) {
		scsynth_android_doOsc(oscMessage.toArray());
	}
	
	public boolean isRunning(){
		return running;
	}
	public boolean isEnded(){
		return ended;
	}
	
	/*
	 * Tell the SuperCollider audio engine to quit, and gracefully exit the audio loop.
	 * This is asynchronous.
	 */
	public void sendQuit(){
		sendMessage(OscMessage.quitMessage());
		running = false;
		closeUDP(); // sent early
	}
	
	/**
	 * The main audio loop lives here- or at least the Java part of it.  Most of the actual
	 * action is done in C++
	 */
	public void run(){
		running = true;
		ended = false;
		@SuppressWarnings("all") // the ternary operator does not contain dead code
		int channelConfiguration = numOutChans==2?
					AudioFormat.CHANNEL_CONFIGURATION_STEREO
					:AudioFormat.CHANNEL_CONFIGURATION_MONO;
		int minSize = AudioTrack.getMinBufferSize(
				sampleRateInHz, 
				channelConfiguration, 
				AudioFormat.ENCODING_PCM_16BIT);
		if(numInChans != 0){
			minSize = Math.max(minSize,
			      AudioRecord.getMinBufferSize(
					sampleRateInHz, 
					channelConfiguration, 
					AudioFormat.ENCODING_PCM_16BIT)
			      );
		}
		
		setPriority(Thread.MAX_PRIORITY);
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		boolean gotRecord=false;
		// instantiate AudioTrack
		try{
			audioTrack = new AudioTrack(
					AudioManager.STREAM_MUSIC, 
					sampleRateInHz, 
					channelConfiguration, 
					AudioFormat.ENCODING_PCM_16BIT, 
					minSize, 
					AudioTrack.MODE_STREAM);
		}catch(IllegalArgumentException e){
			Log.e(TAG, "failed to create AudioTrack object: " + e.getMessage());
			e.printStackTrace();
		}
		if(numInChans != 0){
			// instantiate AudioRecord
			try{
				audioRecord = new AudioRecord(
						MediaRecorder.AudioSource.MIC, 
						sampleRateInHz, 
						channelConfiguration, 
						AudioFormat.ENCODING_PCM_16BIT, 
						minSize);
				gotRecord = (audioRecord.getState()==AudioRecord.STATE_INITIALIZED);
			}catch(IllegalArgumentException e){
				Log.e(TAG, "failed to create AudioRecord object: " + e.getMessage());
				e.printStackTrace();
			}
		}

		audioTrack.play(); // this must be done BEFORE we write data to it
		
		int ndkReturnVal;
		if (gotRecord) {
			audioRecord.startRecording();
			while(running){
				// let the NDK make the sound!
				if(audioRecord.read(audioBuf, 0, bufSizeShorts) != bufSizeShorts){
					//Log deactivated for now at least since we already get 
					//             W/AudioFlinger( 1353): RecordThread: buffer overflow
					//Log.w(TAG, "audioRecord.read didn't read a complete buffer-full");
				}
				ndkReturnVal = scsynth_android_genaudio(audioBuf);
				if(ndkReturnVal!=0) {
					Log.e(TAG,"SCSynth returned non-zero value "+ndkReturnVal);
					running=false;
				}
				audioTrack.write(audioBuf, 0, bufSizeShorts);
				Thread.yield();
			}
		} else {
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
		}
		
		// QUITTING PHASE:
		// running has been set to false (should be by ScService.stop())
		// - this means /quit has been sent to the scsynth code, 
		// so we wait for [/done, /quit] which tells us quit has nicely completed. 
		OscMessage msgFromServer=null;
		int triesToFail = 8; // should be plenty, it should only take 2 cycles (I think) max
		for(int i=0; i<triesToFail; ++i){
			while((!ended) && SCAudio.hasMessages()){
				msgFromServer = SCAudio.getMessage();
				if (msgFromServer != null) {
					String firstToken = msgFromServer.get(0).toString();
					if (firstToken.equals("/done")) {
						String secondToken = msgFromServer.get(1).toString();
						if (secondToken.equals("/quit")) {
							ended = true;
						}
					}
				}
			}
			// now invoke sc audio loop, though we're not reading/writing the actual audio
			if(!ended) ndkReturnVal = scsynth_android_genaudio(audioBuf);
		}
    	
    	if(!ended){
    		Log.e(TAG, "SCAudio attempted quit but didn't detect [/done, /quit] response from supercollider engine. Was [/quit] sent to engine?");
    	}
		
		audioTrack.stop();
		audioTrack.release();
		if(gotRecord){
			audioRecord.stop();
			audioRecord.release();
		}
		ended = true;
	}
	
	/**
	 * Allows non-Android OSC agents to connect to SuperCollider using a
	 * more traditional UDP port.
	 * 
	 * @param port The port to listen on
	 */
	public void openUDP(int port) {
		scsynth_android_open_udp(port);
	}
	/*
	 * Close the UDP port so it doesn't block future invocations
	 */
	public void closeUDP() {
		scsynth_android_close_udp();
	}
	
	/**
	 * Have we got any (asynchronous) response messages from scserver?
	 * 
	 * @return
	 */
	public static boolean hasMessages() { return scsynth_android_hasMessages(); }
	
	/**
	 * If we have any messages from scserver, get one of them and
	 * remove it from the internal mailbox.
	 * 
	 * @return A message or null if there are none to get
	 */
	public static OscMessage getMessage() {return scsynth_android_getMessage(); }
}
