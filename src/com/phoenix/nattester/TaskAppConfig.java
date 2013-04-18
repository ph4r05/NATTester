package com.phoenix.nattester;

import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.service.IServerService;

/**
 * Parameters for ContactlistFetchAsync task
 * @author ph4r05
 *
 */
public class TaskAppConfig {
	private String publicIP;
	private String peerIP;
	private int peerPort=0;
	private int n=10;
	private boolean master=true;
	private AsyncTaskListener updateCallback=null;
	
	private String stunServer="89.29.122.60";
	private int stunPort=3478;
	
	private String txName="";
	private String txServer="";
	private int txServerPort=9999;
	
	private IServerService api=null;
	
	@Override
	public String toString() {
		return "TaskAppConfig [publicIP=" + publicIP + ", peerIP=" + peerIP
				+ ", peerPort=" + peerPort + ", n=" + n + ", master=" + master
				+ ", updateCallback=" + updateCallback + ", stunServer="
				+ stunServer + ", stunPort=" + stunPort + "]";
	}
	public String getPublicIP() {
		return publicIP;
	}
	public void setPublicIP(String publicIP) {
		this.publicIP = publicIP;
	}
	public String getPeerIP() {
		return peerIP;
	}
	public void setPeerIP(String peerIP) {
		this.peerIP = peerIP;
	}
	public int getPeerPort() {
		return peerPort;
	}
	public void setPeerPort(int peerPort) {
		this.peerPort = peerPort;
	}
	public int getN() {
		return n;
	}
	public void setN(int n) {
		this.n = n;
	}
	public boolean isMaster() {
		return master;
	}
	public void setMaster(boolean master) {
		this.master = master;
	}
	public AsyncTaskListener getUpdateCallback() {
		return updateCallback;
	}
	public void setUpdateCallback(AsyncTaskListener updateCallback) {
		this.updateCallback = updateCallback;
	}
	public String getStunServer() {
		return stunServer;
	}
	public void setStunServer(String stunServer) {
		this.stunServer = stunServer;
	}
	public int getStunPort() {
		return stunPort;
	}
	public void setStunPort(int stunPort) {
		this.stunPort = stunPort;
	}
	public IServerService getApi() {
		return api;
	}
	public void setApi(IServerService api) {
		this.api = api;
	}
	public String getTxName() {
		return txName;
	}
	public void setTxName(String txName) {
		this.txName = txName;
	}
	public String getTxServer() {
		return txServer;
	}
	public void setTxServer(String txServer) {
		this.txServer = txServer;
	}
	public int getTxServerPort() {
		return txServerPort;
	}
	public void setTxServerPort(int txServerPort) {
		this.txServerPort = txServerPort;
	}
}
