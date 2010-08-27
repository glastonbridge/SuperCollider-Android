package net.sf.supercollider.android;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.sf.supercollider.android.OscMessage;
import net.sf.supercollider.android.SCAudio;
import net.sf.supercollider.android.ScService;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.test.AndroidTestCase;
import android.util.Log;

/**
 * Essentially these are smoketests, there is no validation step for a lot
 * of the code here, barring "are we still alive?"  Look at the tests in the
 * suite, run them, listen to the audio.  You should hear a series of beeps
 * and squeaks corresponding to the tests, if they sound loosely melodic and
 * there are no crashes, then good.
 * 
 * You can also use this code as an example when instantiating your own synthdefs
 * 
 * @TODO: make validation automatic
 * 
 * @author alex
 *
 */

public class NativeAudioTests extends AndroidTestCase {

	protected static final String TAG = "NativeAudioTests";
	final int numInChans = 1; 
	final int numOutChans = 1; 
	final int shortsPerSample = 1; 
	final int bufSizeFrames = 64*16;  
	final int bufSizeShorts = bufSizeFrames * numOutChans * shortsPerSample; 
	int sampleRateInHz = 11025;
	short[] audioBuf = new short[bufSizeShorts];
	
	public void testSynthDefs() {
		initFiles();
    	System.loadLibrary("sndfile");
    	System.loadLibrary("scsynth"); 
    	SCAudio.scsynth_android_initlogging();
    	SCAudio.scsynth_android_start(sampleRateInHz, bufSizeFrames, numInChans, numOutChans, shortsPerSample, 
				ScService.dllDirStr, ScService.dataDirStr);
    	assert(true); // SC started, have a biscuit
    	
    	///////////////////////////////////////////////////////////////////////
    	// Silence is golden
		assert(0==SCAudio.scsynth_android_genaudio(audioBuf));
		for(short s : audioBuf) assert(s==0);

		assert(true);

		int buffersPerSecond = (sampleRateInHz*shortsPerSample)/(bufSizeShorts*numOutChans);

		AudioTrack audioTrack = createAudioOut(); // audible testing
		try {
			
	    	///////////////////////////////////////////////////////////////////////
	    	// test default.scsyndef
			SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "default", OscMessage.defaultNodeId});

			for(int i=0; i<buffersPerSecond; ++i) {
				SCAudio.scsynth_android_genaudio(audioBuf);
				audioTrack.write(audioBuf, 0, bufSizeShorts);
			}
			
			assert(true);
	
	    	///////////////////////////////////////////////////////////////////////
	    	// Test buffers
			SCAudio.scsynth_android_doOsc(new Object[] {"n_free", OscMessage.defaultNodeId});
			int bufferIndex = 10;
			SCAudio.scsynth_android_doOsc(new Object[] {"b_allocRead", bufferIndex, "/sdcard/supercollider/sounds/a11wlk01.wav"});
			SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "tutor", OscMessage.defaultNodeId});
			
			for(int i=0; i<buffersPerSecond; ++i) {
				SCAudio.scsynth_android_genaudio(audioBuf);
				audioTrack.write(audioBuf, 0, bufSizeShorts);
			}
			
			SCAudio.scsynth_android_doOsc(new Object[] {"n_free", OscMessage.defaultNodeId});
			//SCAudio.scsynth_android_doOsc(new Object[] {"b_free", bufferIndex});
			assert(true);
		} finally {
		    audioTrack.stop();
		}

    	if (!SCAudio.hasMessages()) assert(false);
	}
	
	protected AudioTrack createAudioOut() {
		@SuppressWarnings("all") // the ternary operator does not contain dead code
		int channelConfiguration = numOutChans==2?
				AudioFormat.CHANNEL_CONFIGURATION_STEREO
				:AudioFormat.CHANNEL_CONFIGURATION_MONO;
		int minSize = AudioTrack.getMinBufferSize(
				sampleRateInHz, 
				channelConfiguration, 
				AudioFormat.ENCODING_PCM_16BIT);
		AudioTrack audioTrack = new AudioTrack(
				AudioManager.STREAM_MUSIC, 
				sampleRateInHz, 
				channelConfiguration, 
				AudioFormat.ENCODING_PCM_16BIT, 
				minSize, 
				AudioTrack.MODE_STREAM);
		audioTrack.play();
		return audioTrack;
	}
	
	protected void pipeFile(String assetName, String targetDir) throws IOException {
		InputStream is = getContext().getAssets().open(assetName);
		OutputStream os = new FileOutputStream(targetDir+"/"+assetName);
		byte[] buf = new byte[1024];
		int bytesRead = 0;
		while (-1 != (bytesRead = is.read(buf))) {
			os.write(buf,0,bytesRead);
		}
		is.close();
		os.close();
	}
	
	// For a fresh install, make sure we have our test synthdefs and
	// samples to hand.
	protected boolean initFiles() {
		try {
			File dataDir = new File(ScService.dataDirStr);
			dataDir.mkdirs();
			pipeFile("default.scsyndef",ScService.dataDirStr);
			pipeFile("tutor.scsyndef",ScService.dataDirStr);
			pipeFile("ffttest.scsyndef",ScService.dataDirStr);
			String soundDirStr = "/sdcard/supercollider/sounds";
			File soundDir = new File(soundDirStr);
			soundDir.mkdirs();
			pipeFile("a11wlk01.wav",soundDirStr);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

}
