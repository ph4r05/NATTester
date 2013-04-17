package com.phoenix.nattester;

/**
 * Simple class to publish progress in certificate signing async task.
 * @author ph4r05
 */
public class DefaultAsyncProgress {
	private double percent;
	private String message;
	private String longMessage;
	
	public interface AsyncTaskListener {
		void onTaskUpdate(DefaultAsyncProgress progress, int state);
		void setPublicIP(String IP);
	}
	
	public DefaultAsyncProgress(double percent, String message) {
		this.percent = percent;
		this.message = message;
	}
	
	public DefaultAsyncProgress(double percent, String message, String longMessage) {
		this.percent = percent;
		this.message = message;
		this.longMessage = longMessage;
	}
	
	@Override
	public String toString() {
		return "DefaultAsyncProgress [percent=" + percent + ", message="
				+ message + ", longMessage=" + longMessage + "]";
	}

	public double getPercent() {
		return percent;
	}
	public void setPercent(double percent) {
		this.percent = percent;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getLongMessage() {
		return longMessage;
	}
	public void setLongMessage(String longMessage) {
		this.longMessage = longMessage;
	}
	
}
