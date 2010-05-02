/**
 * @author Peter Kirn peter@createdigitalmedia.net for the pdportable project
 * @stolenshamelesslyby Alex Shaw alex@glastonbridge.com for SuperCollider-Android
 */

package uk.co.mcld.dabble.GlastoCollider1;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

public class ScService extends Service {
	
	/**
	 * Our AIDL implementation to allow a bound Activity to talk to us
	 */
	private final ISuperCollider.Stub mBinder = new ISuperCollider.Stub() {
		@Override
		public void start() throws RemoteException {
			ScService.this.start();
		}
		@Override
		public void stop() throws RemoteException {
			ScService.this.stop();
		}
	};
	
    private int NOTIFICATION_ID = 1;
    private SCAudio audioThread;

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void start() {
		audioThread.start();
	}

	@Override
    public void onCreate() {
		audioThread = new SCAudio(this); 
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
		audioThread.setRunning(false);
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
}
