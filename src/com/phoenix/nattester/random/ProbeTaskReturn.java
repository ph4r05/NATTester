package com.phoenix.nattester.random;

public class ProbeTaskReturn {
	private long initTime;
	private long sendTime;
	private long recvTime;
	private int srcPort;
	private String localAddress;
	private int dstPort;
	private int mappedPort;
	private String mappedAddress;
	private int sendRetryCnt=0;
	private int recvRetryCnt=0;
	private boolean finished=false;
	
	private boolean error=false;
	private int errCode;
	private String errReason;
	
	public ProbeTaskReturn() {
		super();
	}
	
	public ProbeTaskReturn(ProbeTaskParam p) {
		this.srcPort = p.getIntPort();
		this.dstPort = p.getExtPort();
	}
	
	public long getInitTime() {
		return initTime;
	}
	public void setInitTime(long initTime) {
		this.initTime = initTime;
	}
	public long getSendTime() {
		return sendTime;
	}
	public void setSendTime(long sendTime) {
		this.sendTime = sendTime;
	}
	public long getRecvTime() {
		return recvTime;
	}
	public void setRecvTime(long recvTime) {
		this.recvTime = recvTime;
	}
	public int getSrcPort() {
		return srcPort;
	}
	public void setSrcPort(int srcPort) {
		this.srcPort = srcPort;
	}
	public int getDstPort() {
		return dstPort;
	}
	public void setDstPort(int dstPort) {
		this.dstPort = dstPort;
	}
	public int getMappedPort() {
		return mappedPort;
	}
	public void setMappedPort(int mappedPort) {
		this.mappedPort = mappedPort;
	}
	public int getSendRetryCnt() {
		return sendRetryCnt;
	}
	public void setSendRetryCnt(int sendRetryCnt) {
		this.sendRetryCnt = sendRetryCnt;
	}
	public int getRecvRetryCnt() {
		return recvRetryCnt;
	}
	public void setRecvRetryCnt(int recvRetryCnt) {
		this.recvRetryCnt = recvRetryCnt;
	}
	public boolean isFinished() {
		return finished;
	}
	public void setFinished(boolean finished) {
		this.finished = finished;
	}
	public boolean isError() {
		return error;
	}
	public void setError(boolean error) {
		this.error = error;
	}
	public int getErrCode() {
		return errCode;
	}
	public void setErrCode(int errCode) {
		this.errCode = errCode;
	}
	public String getErrReason() {
		return errReason;
	}
	public void setErrReason(String errReason) {
		this.errReason = errReason;
	}
	public String getMappedAddress() {
		return mappedAddress;
	}

	public void setMappedAddress(String mappedAddress) {
		this.mappedAddress = mappedAddress;
	}

	public String getLocalAddress() {
		return localAddress;
	}

	public void setLocalAddress(String localAddress) {
		this.localAddress = localAddress;
	}
	
}
