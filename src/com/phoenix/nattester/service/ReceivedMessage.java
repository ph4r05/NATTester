package com.phoenix.nattester.service;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.phoenix.nattester.AlgTask;

import android.os.Parcel;
import android.os.Parcelable;

public class ReceivedMessage implements Parcelable {
	private static final Logger LOGGER = LoggerFactory.getLogger(AlgTask.class);
	
	private String sourceIP;
	private int sourcePort;
	private long milliReceived;
	private byte[] message;

	
	public ReceivedMessage(){
		
	}
	
	/**
     * Construct from parcelable <br/>
     * Only used by {@link #CREATOR}
     * 
     * @param in parcelable to build from
     */
    public ReceivedMessage(Parcel in) {
    	this.readFromParcel(in);
    }
    
    /**
     * Parcelable creator. So that it can be passed as an argument of the aidl
     * interface
     */
    public static final Parcelable.Creator<ReceivedMessage> CREATOR = new Parcelable.Creator<ReceivedMessage>() {
        public ReceivedMessage createFromParcel(Parcel in) {
            return new ReceivedMessage(in);
        }

        public ReceivedMessage[] newArray(int size) {
            return new ReceivedMessage[size];
        }
    };

	
	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public final void readFromParcel(Parcel in){
		try {
			sourceIP = in.readString();
			sourcePort = in.readInt();
			milliReceived = in.readLong();
			message = in.createByteArray();
		} catch(Exception ex){
			LOGGER.error("Cannot read message from parcel", ex);
		}
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		try {
			dest.writeString(sourceIP);
			dest.writeInt(sourcePort);
			dest.writeLong(milliReceived);
			dest.writeByteArray(message);
		} catch(Exception ex){
			LOGGER.error("Cannot write message tom parcel", ex);
		}
	}

	@Override
	public String toString() {
		return "ReceivedMessage [sourceIP=" + sourceIP + ", sourcePort="
				+ sourcePort + ", milliReceived=" + milliReceived
				+ ", message=" + Arrays.toString(message) + "]";
	}

	public String getSourceIP() {
		return sourceIP;
	}

	public void setSourceIP(String sourceIP) {
		this.sourceIP = sourceIP;
	}

	public int getSourcePort() {
		return sourcePort;
	}

	public void setSourcePort(int sourcePort) {
		this.sourcePort = sourcePort;
	}

	public long getMilliReceived() {
		return milliReceived;
	}

	public void setMilliReceived(long milliReceived) {
		this.milliReceived = milliReceived;
	}

	public byte[] getMessage() {
		return message;
	}

	public void setMessage(byte[] message) {
		this.message = message;
	}
}
