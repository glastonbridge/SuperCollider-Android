package uk.co.mcld.dabble.GlastoCollider1;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class GlastoCollider1 extends Activity {
	
	DanAudioThread audioThread;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TextView tv = new TextView(this);
		tv.setText("Hello dandroid 1.6");
		setContentView(tv);

		// Then create the audio thread
		audioThread = new DanAudioThread(this);
		audioThread.start();
		tv.setText("ok i created an audio thread...");

		//tv.setText("ok i've outputted it");

	}
	
	// Called by Android API when not the front app any more. For this one we'll quit
	@Override
	public void onStop(){
		super.onStop();
		audioThread.setRunning(false);
		while(!audioThread.isEnded()){
			try{
				Thread.sleep(50L);
			}catch(InterruptedException err){
				err.printStackTrace();
				break;
			}
		}
		//finish(); // ask to end when not front
	}
}

