package com.phoenix.nattester.benchmark;

public class BenchRecord {
	int portCount;
	int delay;
	int testNumber;
	long timeStarted;
	long timeTXCompleted;
	String txid;
	int receivedMsgs;
	long firstReceivedMsg;
	
	String localIP;
	String publicIP;
	int publicPort;
	
	String remoteIP;
	int remotePort;
	String remoteAnswers;
	
	public String toRec(){
		StringBuilder sb = new StringBuilder();
		sb.append(portCount).append(";")
		  .append(delay).append(";")
		  .append(testNumber).append(";")
		  .append(timeStarted).append(";")
		  .append(timeTXCompleted).append(";")
		  .append(txid).append(";")
		  .append(receivedMsgs).append(";")
		  .append(firstReceivedMsg).append(";")
		  .append(localIP).append(";")
		  .append(publicIP).append(";")
		  .append(publicPort).append(";")
		  .append(remoteIP).append(";")
		  .append(remotePort).append(";")
		  .append(remoteAnswers).append(";");
		return sb.toString();
	}
	
	@Override
	public String toString() {
		return "BenchRecord [portCount=" + portCount + ", delay=" + delay
				+ ", testNumber=" + testNumber + ", timeStarted=" + timeStarted
				+ ", timeTXCompleted=" + timeTXCompleted + ", txid=" + txid
				+ ", receivedMsgs=" + receivedMsgs + ", firstReceivedMsg="
				+ firstReceivedMsg + "]";
	}
	
	public int getPortCount() {
		return portCount;
	}
	public void setPortCount(int portCount) {
		this.portCount = portCount;
	}
	public int getTestNumber() {
		return testNumber;
	}
	public void setTestNumber(int testNumber) {
		this.testNumber = testNumber;
	}
	public long getTimeStarted() {
		return timeStarted;
	}
	public void setTimeStarted(long timeStarted) {
		this.timeStarted = timeStarted;
	}
	public long getTimeTXCompleted() {
		return timeTXCompleted;
	}
	public void setTimeTXCompleted(long timeTXCompleted) {
		this.timeTXCompleted = timeTXCompleted;
	}
	public String getTxid() {
		return txid;
	}
	public void setTxid(String txid) {
		this.txid = txid;
	}
	public int getReceivedMsgs() {
		return receivedMsgs;
	}
	public void setReceivedMsgs(int receivedMsgs) {
		this.receivedMsgs = receivedMsgs;
	}
	public long getFirstReceivedMsg() {
		return firstReceivedMsg;
	}
	public void setFirstReceivedMsg(long firstReceivedMsg) {
		this.firstReceivedMsg = firstReceivedMsg;
	}
}
