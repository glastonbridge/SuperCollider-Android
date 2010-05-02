package uk.co.mcld.dabble.GlastoCollider1;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
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
	private ServiceConnection conn = new ScServiceConnection();
	private ISuperCollider.Stub superCollider;
	private class ScServiceConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			SuperColliderActivity.this.superCollider = (ISuperCollider.Stub) service;
			try {
				superCollider.start();
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
		@Override
		public void onServiceDisconnected(ComponentName name) {

		}
	}
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		TextView tv = new TextView(this);
		setContentView(tv);
		bindService(new Intent("supercollider.START_SERVICE"),conn,BIND_AUTO_CREATE);
		// Then create the audio thread
		tv.setTypeface(Typeface.MONOSPACE);
		tv.setTextSize(10);
		tv.setText("\n"
				 + "                  ~+I777?                \n"
				 + "           :++?I77I====~?I7I             \n"
				 + "     ~=~+I77I??===+?IIII++~+?7           \n"
				 + " 77I~?777+===+?????+++?+II??~==7 ,      \n"
				 + " 7777 I~=II7?++=?+????++=+?IIII~~~+7:   \n"
				 + " I7=I?777 ~~+?7I?+==++?II?++=~~+I7I+++  \n"
				 + " ?7~, ~?=+777~~=+7IIII+~~=+??++++++,,+  \n"
				 + " ?7~      =~+ 77~~~~~=++++++=:,,,,  :+  \n"
				 + " +7= ??=~=      77++++==~~~:,   ,:, ,=  \n"
				 + " +7= +?+???+?=,, 7++:     ,:~~~===, ,=  \n"
				 + " =7= ++=+==???I, I=+   ,,:~=====~=, ,~  \n"
				 + " ~7+ =+=     +I: ?=+,  ==~~     ~=: ,~  \n"
				 + " ~7? ~+=  =~ +I: ?~+,  ~~       ~=: ,:  \n"
				 + " :7? ~++  == =I~ +~+:  ~~   ~::  =~ ,:  \n"
				 + " :7?  +++++= =I~ +~+:  :~   ~~:  =~ ,:  \n"
				 + " ,7I=    =~~ ~I= =:+:  :=~~~~~:  =~  ,  \n"
				 + "  7III~~      I+ ~:+~  :=~~~     ==  ,  \n"
				 + "    I??7II=+=,I+ ~,+~       :~~====     \n"
				 + "        ++=7II?I? :,+=  ,:::~=+==+=:     \n"
				 + "        ,  =~:7I? , +=~=++++=~::,,,      \n"
				 + "              :,  , ++===~~~,            \n"
				 + "              ,     +~     ,             \n"
				 + "                 ,                       \n"
             );
	}
	
	@Override
	public void onPause() {
		super.onPause();
		try {
			superCollider.stop();
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

}

