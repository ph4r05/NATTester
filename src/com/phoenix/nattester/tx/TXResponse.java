package com.phoenix.nattester.tx;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

// TXAnswer should be: txstarted|alfa|1365008803|1365008806|PEERS|127.0.0.1;56380;hta;default|127.0.0.1;38918;htc;default|PARAMETERS|fullDelay=3000
public class TXResponse {
	private String method="txstarted";
	private String txname="";
	private long txTimeBegin;
	private long txTimeStarted;
	private TXPeer peers[];
	private HashMap<String, String> parameters;
	
	public static TXResponse fromTxAnswer(String txanswer){
		TXResponse ret = new TXResponse();
		String mainSplits[] = txanswer.split("\\|");
		if (mainSplits[0].equalsIgnoreCase("txstarted")==false){
			throw new IllegalArgumentException("Method!=txstarted; method=["+mainSplits[0]+"]; splits=" + mainSplits.length);
		}
		
		ret.setTxname(mainSplits[1]);
		ret.setTxTimeBegin(Long.parseLong(mainSplits[2]));
		ret.setTxTimeStarted(Long.parseLong(mainSplits[3]));
		if (mainSplits[4].equalsIgnoreCase("PEERS")==false){
			throw new IllegalArgumentException("PEERS part is missing");
		}
		
		int curPos = 5;
		int n = mainSplits.length;
		ArrayList<TXPeer> txArray = new ArrayList<TXPeer>();
		for(; curPos < n && mainSplits[curPos].equalsIgnoreCase("PARAMETERS")==false; curPos++){
			String peerSplits[] = mainSplits[curPos].split(";");
			TXPeer txpeer = new TXPeer();
			txpeer.setIP(peerSplits[0]);
			txpeer.setPort(Integer.parseInt(peerSplits[1]));
			txpeer.setId(peerSplits[2]);
			txpeer.setNatType(peerSplits[3]);
			txArray.add(txpeer);
		}
		
		TXPeer[] txArr = new TXPeer[txArray.size()];
		txArray.toArray(txArr);
		ret.setPeers(txArr);
		
		HashMap<String, String> params = new HashMap<String, String>();
		ret.setParameters(params);
		
		if (curPos==n || mainSplits[curPos].equalsIgnoreCase("PARAMETERS")==false) return ret;
		for(; curPos+1 < n; curPos++){
			String parSplit[] = mainSplits[curPos+1].split("=");
			params.put(parSplit[0], parSplit[1]);
		}
		
		ret.setParameters(params);
		return ret;
	}
	
	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}
	public String getTxname() {
		return txname;
	}
	public void setTxname(String txname) {
		this.txname = txname;
	}
	public long getTxTimeBegin() {
		return txTimeBegin;
	}
	public void setTxTimeBegin(long txTimeBegin) {
		this.txTimeBegin = txTimeBegin;
	}
	public long getTxTimeStarted() {
		return txTimeStarted;
	}
	public void setTxTimeStarted(long txTimeStarted) {
		this.txTimeStarted = txTimeStarted;
	}
	public TXPeer[] getPeers() {
		return peers;
	}
	public void setPeers(TXPeer[] peers) {
		this.peers = peers;
	}
	public HashMap<String, String> getParameters() {
		return parameters;
	}
	public void setParameters(HashMap<String, String> parameters) {
		this.parameters = parameters;
	}
	
	@Override
	public String toString() {
		return "TXResponse [method=" + method + ", txname=" + txname
				+ ", txTimeBegin=" + txTimeBegin + ", txTimeStarted="
				+ txTimeStarted + ", peers=" + Arrays.toString(peers)
				+ ", parameters=" + parameters + "]";
	}
	
	
}
