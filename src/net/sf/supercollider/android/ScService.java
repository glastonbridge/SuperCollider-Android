/**
 * @author Peter Kirn peter@createdigitalmedia.net for the pdportable project
 * @stolenshamelesslyby Alex Shaw alex@glastonbridge.com for SuperCollider-Android
 */

package net.sf.supercollider.android;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class ScService extends Service { 

	public static final String scDirStr = "/sdcard/supercollider";
	public static final String dataDirStr = scDirStr+"/synthdefs";
	public static final String dllDirStr = "/data/data/net.sf.supercollider.android/lib"; // TODO: not very extensible, hard coded, generally sucks

	/**
	 * Our AIDL implementation to allow a bound Activity to talk to us
	 */
	private final ISuperCollider.Stub mBinder = new ISuperCollider.Stub() {
		//@Override
		public void start() throws RemoteException {
			ScService.this.start();
		}
		//@Override
		public void stop() throws RemoteException {
			ScService.this.stop();
		}
		//@Override
		public void sendMessage(OscMessage oscMessage) throws RemoteException {
			ScService.this.audioThread.sendMessage(oscMessage);
		}
		public void openUDP(int port) throws RemoteException {
			ScService.this.audioThread.openUDP(port);
		}
		public void closeUDP() throws RemoteException {
			ScService.this.audioThread.closeUDP();
		}
		public void sendQuit() throws RemoteException {
			ScService.this.audioThread.sendQuit();
		}
		
	};
	
    private int NOTIFICATION_ID = 1;
    private SCAudio audioThread;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void start() {
		if (audioThread == null || !audioThread.isRunning() ) {
			audioThread = new SCAudio(dllDirStr);
			audioThread.start();
		}
	}

	@Override
    public void onCreate() {
		audioThread = null;
		File dataDir = new File(dataDirStr);
		if(dataDir.mkdirs()) {  
			deliverDefaultSynthDefs();
		} else if (!dataDir.isDirectory()) {
			//1
			String ns = Context.NOTIFICATION_SERVICE;
			NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
			//2
			int icon = R.drawable.icon;
			CharSequence errorText = "Could not establish data folder. Try unmounting SD card from host";
			Notification mNotification = new Notification(icon, errorText, System.currentTimeMillis());
			//3
			Context context = getApplicationContext();
			CharSequence errorTitle = "SuperCollider error";
			Intent notificationIntent = new Intent(this, ScService.class);
			PendingIntent errorIntent = PendingIntent.getActivity(context, 0, notificationIntent, 0);
			mNotification.setLatestEventInfo(context, errorTitle, errorText, errorIntent);
			//4
			mNotificationManager.notify(1, mNotification);
			
			Log.e(SCAudio.TAG,"Could not create directory " + dataDirStr);
		}
    }

    /* onStart is called for Android versions < 2.0, but onStartCommand is 
       called for Android 2.0 and above.  Anything which is generic to all
       Android releases should be declared in onStart.  Any newer features
       should be declared in onStartCommand.  Access new features using 
       * reflection, to facilitate compilation to 1.5 and 1.6
    */
    @Override
    public void onStart(Intent intent, int startId) { }
    public int onStartCommand(Intent intent, int flags, int startId) {
     	onStart(intent, startId);
        int START_STICKY = 1;
        try {
            // Android 2.1 API allows us to specify that this service is a foreground task
            Notification notification = new Notification(R.drawable.icon,
                    getText(R.string.app_name), System.currentTimeMillis());
            Class<?> superClass = super.getClass();
            Method startForeground = superClass.getMethod("startForeground",
                new Class[] {
                    int.class,
                    Class.forName("android.app.Notification")
                }
            );
            Field startStickyValue = superClass.getField("START_STICKY");
            START_STICKY=startStickyValue.getInt(null);
            startForeground.invoke(this, new Object[] {
                NOTIFICATION_ID, 
                notification}
            );
        } catch (Exception nsme) {
            // We can't get the newer methods
        }
        return START_STICKY;
    }
	
    public void stop() {
		try {
			mBinder.sendQuit();
		} catch (RemoteException re) {
			re.printStackTrace();
		} 
		while(!audioThread.isEnded()){
			try{
				Thread.sleep(50L);
			}catch(InterruptedException err){
				err.printStackTrace();
				break;
			}
		}
    }
    
	// Called by Android API when not the front app any more. For this one we'll quit
	@Override
	public void onDestroy(){
		stop();
		super.onDestroy();
	}

	/**
	 * Copies the default synth defs out, ScService calls it the first time the supercollider
	 * data dir is created.
	 */
	public void deliverDefaultSynthDefs() {
		try {
			InputStream is = getAssets().open("default.scsyndef");
			OutputStream os = new FileOutputStream("/sdcard/supercollider/synthdefs/default.scsyndef");
			byte[] buf = new byte[1024];
			int bytesRead = 0;
			while (-1 != (bytesRead = is.read(buf))) {
				os.write(buf,0,bytesRead);
			}
			is.close();
			os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
