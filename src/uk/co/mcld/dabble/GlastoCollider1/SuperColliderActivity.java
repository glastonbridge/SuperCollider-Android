package uk.co.mcld.dabble.GlastoCollider1;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Provides a quick-and-dirty entry point for hacking
 * together the Android interface onto SC.
 * 
 * If we're testing SC-Android interface logic in here,
 * move it out into a providing class once it's stable
 * so that we can track library code.
 * 
 * TODO: Create a location for the library code.
 * 
 * TODO: One day this will all be unit tests, but the
 * current setup is IMO a faster way to develop for now
 * --alexs 20100310
 * 
 * @author Dan Stowell
 *
 */
public class SuperColliderActivity extends Activity {
	
	SCAudio audioThread;
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		TextView tv = new TextView(this);
		tv.setText("Hello dandroid 1.6");
		setContentView(tv);

		// Then create the audio thread
		audioThread = new SCAudio(this); 
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

