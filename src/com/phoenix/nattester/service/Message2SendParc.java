package com.phoenix.nattester.service;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Parcel;
import android.os.Parcelable;

public class Message2SendParc implements Parcelable {
		private static final Logger LOGGER = LoggerFactory.getLogger(Message2SendParc.class);
		
		private String sourceIP;
		private int sourcePort;
		private byte[] message;

		
		public Message2SendParc(){
			
		}
		
		/**
	     * Construct from parcelable <br/>
	     * Only used by {@link #CREATOR}
	     * 
	     * @param in parcelable to build from
	     */
	    public Message2SendParc(Parcel in) {
	    	this.readFromParcel(in);
	    }
	    
	    /**
	     * Parcelable creator. So that it can be passed as an argument of the aidl
	     * interface
	     */
	    public static final Parcelable.Creator<Message2SendParc> CREATOR = new Parcelable.Creator<Message2SendParc>() {
	        public Message2SendParc createFromParcel(Parcel in) {
	            return new Message2SendParc(in);
	        }

	        public Message2SendParc[] newArray(int size) {
	            return new Message2SendParc[size];
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
				dest.writeByteArray(message);
			} catch(Exception ex){
				LOGGER.error("Cannot write message tom parcel", ex);
			}
		}

		@Override
		public String toString() {
			return "ReceivedMessage [sourceIP=" + sourceIP + ", sourcePort="
					+ sourcePort
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

		public byte[] getMessage() {
			return message;
		}

		public void setMessage(byte[] message) {
			this.message = message;
		}
}
