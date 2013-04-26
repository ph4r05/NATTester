package com.phoenix.nattester;

import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.service.IServerService;

/**
 * Parameters for ContactlistFetchAsync task
 * @author ph4r05
 *
 */
public class TaskAppConfig {
	private String localIP;
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
	private String txId="";
	
	private IServerService api=null;
	
	
	public TaskAppConfig(){
		
	}
	
	public TaskAppConfig(TaskAppConfig s){
		this.localIP = s.localIP;
		this.publicIP = s.publicIP;
		this.peerIP = s.peerIP;
		this.peerPort = s.peerPort;
		this.n = s.n;
		this.master=s.master;
		this.updateCallback=s.updateCallback;
		this.stunServer=s.stunServer;
		this.stunPort=s.stunPort;
		this.txName=s.txName;
		this.txServer=s.txServer;
		this.txServerPort=s.txServerPort;
		this.txId=s.txId;
		this.api=s.api;
	}
	
	
	@Override
	public String toString() {
		return "TaskAppConfig [localIP=" + localIP + ", publicIP=" + publicIP
				+ ", peerIP=" + peerIP + ", peerPort=" + peerPort + ", n=" + n
				+ ", master=" + master + ", updateCallback=" + updateCallback
				+ ", stunServer=" + stunServer + ", stunPort=" + stunPort
				+ ", txName=" + txName + ", txServer=" + txServer
				+ ", txServerPort=" + txServerPort + ", txId=" + txId
				+ ", api=" + api + "]";
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
	public String getTxId() {
		return txId;
	}
	public void setTxId(String txId) {
		this.txId = txId;
	}
	public String getLocalIP() {
		return localIP;
	}
	public void setLocalIP(String localIP) {
		this.localIP = localIP;
	}
}
