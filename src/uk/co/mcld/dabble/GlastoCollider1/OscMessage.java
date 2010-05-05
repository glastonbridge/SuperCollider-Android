package uk.co.mcld.dabble.GlastoCollider1;

import java.util.LinkedList;

import android.os.Parcel;
import android.os.Parcelable;

/** A Simple pastiche of the SuperCollider packet in Java land.  The internal
 *  representation is simple java objects, which are converted to a buffer in 
 *  c++ later.
 * 
 * Usage: Add string command, add arguments.
 *  
 * @author alex
 *
 */
public final class OscMessage implements Parcelable {
	
	public static final int defaultNodeId = 1000; // What is this?  Taken from OSCMessages.h

	///////////////////////////////////////////////////////////////////////////
	// Static templates for common operations
	///////////////////////////////////////////////////////////////////////////
	
	public static OscMessage createSynthMessage(String name) {
		OscMessage synthMessage = new OscMessage();
		synthMessage.add("/s_new");
		synthMessage.add(name);
		synthMessage.add(defaultNodeId);
		return synthMessage;
	}
	
	public static OscMessage noteMessage(int note, int velocity) {
	    OscMessage retval =  new OscMessage();
	    
	    OscMessage notebundle = new OscMessage();
	    notebundle.add("/n_set");
	    notebundle.add(defaultNodeId);
		notebundle.add("/note");
	    notebundle.add(note);

	    OscMessage velbundle = new OscMessage();
		velbundle.add("/n_set");
	    velbundle.add(defaultNodeId);
		velbundle.add("/velocity");
	    velbundle.add(velocity);

	    retval.add(notebundle);
	    retval.add(velbundle);
	    return retval;
	}

	///////////////////////////////////////////////////////////////////////////
	// The actual OscMessage implementation
	///////////////////////////////////////////////////////////////////////////
	
	private LinkedList<Object> message = new LinkedList<Object>();
	
	public boolean add(int i) { return message.add(i); }
	public boolean add(float f) { return message.add(f); }
	public boolean add(String s) { return message.add(s); }
	public boolean add(long ii) {return message.add(ii); }
	public boolean add(OscMessage m) {return message.add(m);}
	public Object[] toArray() { return message.toArray(); }
	
	///////////////////////////////////////////////////////////////////////////
	// Parcelling code for AIDL 
	///////////////////////////////////////////////////////////////////////////
	
	public static final Parcelable.Creator<OscMessage> CREATOR = new Parcelable.Creator<OscMessage>() {
		@Override
		public OscMessage createFromParcel(Parcel source) {
			OscMessage retval = new OscMessage();
			source.readList(retval.message, null);
			return retval;
		}

		@Override
		public OscMessage[] newArray(int size) {
			OscMessage[] retval = new OscMessage[size];
			for(int i = 0; i<size;++i) retval[i] = new OscMessage();
			return retval;
		}
	};
	
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeList(message);
	}
}
