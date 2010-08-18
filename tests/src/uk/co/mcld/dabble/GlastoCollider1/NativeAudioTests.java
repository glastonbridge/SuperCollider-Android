package uk.co.mcld.dabble.GlastoCollider1;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import uk.co.mcld.dabble.GlastoCollider1.OscMessage;
import uk.co.mcld.dabble.GlastoCollider1.SCAudio;
import uk.co.mcld.dabble.GlastoCollider1.ScService;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.test.AndroidTestCase;

public class NativeAudioTests extends AndroidTestCase {

	final int numInChans = 1; 
	final int numOutChans = 1; 
	final int shortsPerSample = 1; 
	final int bufSizeFrames = 64*16;  
	final int bufSizeShorts = bufSizeFrames * numOutChans * shortsPerSample; 
	int sampleRateInHz = 11025;
	short[] audioBuf = new short[bufSizeShorts];

	public void testSynthDefs() {
		assert(initFiles());
    	System.loadLibrary("sndfile");
    	System.loadLibrary("scsynth"); 
    	SCAudio.scsynth_android_initlogging();
    	SCAudio.scsynth_android_start(sampleRateInHz, bufSizeFrames, numInChans, numOutChans, shortsPerSample, 
				ScService.dllDirStr, ScService.dataDirStr);
    	assert(true); // SC started, have a biscuit
    	
    	// Silence is golden
		assert(0==SCAudio.scsynth_android_genaudio(audioBuf));
		for(short s : audioBuf) assert(s==0);
		
		SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "default", OscMessage.defaultNodeId});

		assert(true);

		AudioTrack audioTrack = createAudioOut(); // audible testing
		
		int buffersPerSecond = (sampleRateInHz*shortsPerSample)/(bufSizeShorts*numOutChans);
		for(int i=0; i<buffersPerSecond; ++i) {
			SCAudio.scsynth_android_genaudio(audioBuf);
			audioTrack.write(audioBuf, 0, bufSizeShorts);
		}
		
		assert(true);
		
		SCAudio.scsynth_android_doOsc(new Object[] {"n_free", 1000});
		SCAudio.scsynth_android_doOsc(new Object[] {"b_allocRead", 10, "/sdcard/supercollider/sounds/a11wlk01.wav"});
		SCAudio.scsynth_android_doOsc(new Object[] {"s_new", "tutor", OscMessage.defaultNodeId});
		
		for(int i=0; i<buffersPerSecond; ++i) {
			SCAudio.scsynth_android_genaudio(audioBuf);
			audioTrack.write(audioBuf, 0, bufSizeShorts);
		}
		
		audioTrack.stop();
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
			
			String soundDirStr = "/sdcard/supercollider/sounds";
			File soundDir = new File(soundDirStr);
			soundDir.mkdirs();
			pipeFile("default.scsyndef",soundDirStr);
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

}
