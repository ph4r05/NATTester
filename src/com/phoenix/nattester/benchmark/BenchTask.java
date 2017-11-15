package com.phoenix.nattester.benchmark;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.http.entity.FileEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.view.KeyEvent;

import com.phoenix.nattester.DefaultAsyncProgress;
import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.GuiLogger;
import com.phoenix.nattester.MessageInterface;
import com.phoenix.nattester.MessageObserver;
import com.phoenix.nattester.ParametersFragment;
import com.phoenix.nattester.TaskAppConfig;
import com.phoenix.nattester.Utils;
import com.phoenix.nattester.random.ProbeTaskReturn;
import com.phoenix.nattester.service.Message2SendParc;
import com.phoenix.nattester.service.ReceivedMessage;
import com.phoenix.nattester.tx.TXPeer;
import com.phoenix.nattester.tx.TXResponse;

/**
 * Async task for NAT detection	
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class BenchTask extends AsyncTask<BenchTaskParam, DefaultAsyncProgress, Exception> 
	implements OnKeyListener, MessageInterface, MessageObserver {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BenchTask.class);
	public final static String TAG = "BenchTask";
	
	// where to publish progress
	private ProgressDialog dialog = null;
	// context
	private Context context = null;
	// resources - from strings
	private Resources resources = null;
	private ParametersFragment frag = null;
	
	private AsyncTaskListener callback;
	private GuiLogger guiLogger;
	
	private BenchTaskParam cfg;
	private String publicIP=null;
	
	// Write test results to the log file
	private FileWriter fileWriter = null;
	// File used to store the results
	private File file = null;
	
	//
	// Block for incoming message counter structure
	//
	private boolean runningTest=false;  // if not running the test, ignore received message
	private int receivedMessages = 0;
	private long firstMessageReceived = -1;
	
	
	//
	// Configuration block of the test
	//
	static final int[] cfgPortN  = {10, 15};                       // number of ports to use in traversal algorithm
	static final int[] cfgDelay  = {0, 2000, 4000, 8000, 16000}; //{0, 125, 250, 500, 1000, 2000, 4000, 8000, 16000}; // delays in introducer
	static final int   testCount = 10;                                // how many tests to perform in one configuration
	static final int   maxTestID = testCount * cfgDelay.length * cfgPortN.length;
	static final long traverseTime  = 10000;							  // number of milliseconds after transaction was connected to keep connecting
	static final long postTestPause = 10000;							  // milliseconds to wait after test is finished before starting the new test
	
	//
	// State values of the test state automaton
	//
	private class CurTestParam {
		int curPortIdx=0;
    	int curDelayIdx=0;
    	int curTestIdx=0;
    	
    	public CurTestParam(){
    		;
    	}
    	
    	public CurTestParam(int testID){
    		int tmpVal = testID;
        	curPortIdx  = tmpVal / (testCount * cfgDelay.length); tmpVal -= curPortIdx * (testCount * cfgDelay.length);
        	curDelayIdx = tmpVal / (testCount);                   tmpVal -= curDelayIdx * testCount;
        	curTestIdx  = tmpVal;
    	}
    	
    	public int toTestID(){
    		return    curTestIdx 
    				+ curDelayIdx * testCount 
    				+ curPortIdx  * testCount * cfgDelay.length;
    	}
	};
	
	
	private String localIP;
	private long testStarted = 0;
	private long txStartTime = 0; // when was the transaction started
	private long txConnectedTime = 0;
	private int curTestID;
	private HashSet<Integer> recvPorts = new HashSet<Integer>(32);
	private CurTestParam testParam = null;
	
	
	protected CurTestParam getTestParamFromID(int testID){
		return new CurTestParam(testID);
	}
	
	protected String getTxName(String base, CurTestParam p){
		return base 
				+ "_" + cfgPortN[p.curPortIdx]
				+ "_" + cfgDelay[p.curDelayIdx]
				+ "_" + p.curTestIdx;
	}
	
	protected void initFile(){
		// Get the directory for the user's public pictures directory. 
        file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "benchmark-"+System.currentTimeMillis()+".txt");
        LOGGER.debug("Result file is: " + file.getAbsolutePath() + "; is file=" + file.isFile());
		try {
			fileWriter = new FileWriter(file, true);
		} catch (FileNotFoundException e) {
			LOGGER.error("File not found exception", e);
			fileWriter = null;
		} catch (Exception e){
			LOGGER.error("Generic exception", e);
			fileWriter = null;
		}
	}
	
	protected void deinitFile(){
		if (fileWriter==null) return;
		try {
			fileWriter.flush();
			fileWriter.close();
		} catch(Exception e){
			LOGGER.warn("Cannot close file writer", e);
		}
	}
	
	@Override
	protected Exception doInBackground(BenchTaskParam... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		this.cfg = arg0[0];
		int txMaxTimeout = 1000*90;	// transaction timeout value in milliseconds
		int localPort = 34567;
		this.publishProgress(new DefaultAsyncProgress(0.05, "Running tests"));
		this.initFile();
		String logMsg="";
		
		try {
			// UDP datagram
			DatagramSocket socketTest1 = null;
			String address2connect = cfg.getCfg().getStunServer();
	        InetAddress address2connectInet;
			address2connectInet = InetAddress.getByName(cfg.getCfg().getTxServer());
        
	        // obtain private IP
	        localIP = Utils.getIPAddress(true);
	        InetAddress iaddress = InetAddress.getByName(localIP);
	        LOGGER.debug("Local IP address obtained: " + localIP);
	        this.publishProgress(new DefaultAsyncProgress(0.05, "Local IP obtained", "LocalIP: " + localIP));
	        
	        // base to generate transaction name for tests
	        String txBase = cfg.getCfg().getTxName();
	        
	        //
	        // Experiment state
	        //	        
	        for(curTestID = 0; curTestID < maxTestID && wasCancelled()==false; curTestID++){
	        	// testID = testCountIdx + 10*cfgDelayIdx + 10*6*cfgPortIdx
	        	testParam = getTestParamFromID(curTestID);
	        	int curTXServerPort = cfg.getCfg().getTxServerPort() + (curTestID % 95);
	        	
	        	// set number of ports we want to use
	        	cfg.getCfg().setN(cfgPortN[testParam.curPortIdx]);
                
		        //
		        // Phase 1 - start transaction here
		        //
		        
		        // Here is stored log from scan - to detect address/port sensitivity
		        // Timeout for transaction is set rather high since we may want to simulate busy network by this timeouts
		        int timeout = cfgDelay[testParam.curDelayIdx] + 400;
		        String curTxName = getTxName(txBase, testParam);
		        localIP = Utils.getIPAddress(true);
		        txStartTime = System.currentTimeMillis();
		        
		        logMsg = "Starting new test: tx=["+curTxName+"]";
                LOGGER.debug(logMsg);
                this.addMessage(logMsg);
		        
		        // this while represents simple packet request reply cycle. (problem may occur -> re-sending)
		        while (true) {
		            try {
		            	if (wasCancelled()){
		            		return null;
		            	}
		            	
		                // build simple TX request on stun server from my interface and static local port
		                socketTest1 = new DatagramSocket(new InetSocketAddress(iaddress, localPort));
		                socketTest1.setReuseAddress(true);
		                socketTest1.connect(address2connectInet, curTXServerPort);
		                socketTest1.setSoTimeout(timeout);
		
		                // build transaction request
		                String myId = localIP+"-"+cfg.getCfg().getPublicIP()+"-"+cfg.getCfg().getTxId();
		                String txreq = "txbegin|"+curTxName+"|"+myId+"|default|fullDelay=" + cfgDelay[testParam.curDelayIdx];
		                byte[] data = txreq.getBytes("UTF-8");
		                DatagramPacket send = new DatagramPacket(data, data.length);
		                
		                logMsg = "TXBegin request: ["+txreq+"] sent to server " + address2connect + ":" + curTXServerPort;
		                LOGGER.debug(logMsg);
		                this.addMessage(logMsg);
		                
		                // timeouted waiting for transaction
		                String txanswer = "";
		                while(true){
		                	try {
		                		if (wasCancelled()) return null;
		                		
		                		// send transaction request here, in loop since it may be lost as well
		                		socketTest1.send(send);
		                		
		                		// wait for our packet - transaction started?
		                        DatagramPacket receive = new DatagramPacket(new byte[4096], 4096);
		                        socketTest1.receive(receive);
		                        byte[] txanswerByte = receive.getData();
		                        txanswer = new String(txanswerByte, "UTF-8");
		                        // remove all non-ascii characters
		                        txanswer = txanswer.replaceAll("[^\\x00-\\x7F]", "").trim();
		                        
		                        break;
		                	} catch(SocketTimeoutException ste) {
		                		long txCurTime = System.currentTimeMillis();
			                	if (txCurTime > (txStartTime + txMaxTimeout)){
			                		LOGGER.error("Transaction timeouted", ste);
			                		throw ste;
			                	}
			                	
			                	if (wasCancelled()) return null;
		                	}
		                }
		                
	                    // TXAnswer should be: txstarted|alfa|1365008803|1365008806|PEERS|127.0.0.1;56380;hta;default|127.0.0.1;38918;htc;default|PARAMETERS|fullDelay=3000
	                    logMsg = "Transaction response: [" + txanswer + "]";
		                LOGGER.debug(logMsg);
		                this.addMessage(logMsg);
	                    this.publishProgress(new DefaultAsyncProgress(1.0, "Transaction response received"));
	                    if (txanswer==null || txanswer.length()==0 || txanswer.startsWith("txstarted")==false){
	                    	LOGGER.warn("Transaction answer not recognized");
	                    	return null;
	                    }
	                    
	                    // parse answer
	                    if (wasCancelled()) return null;
	                    TXResponse txresp = TXResponse.fromTxAnswer(txanswer);
	                    
	                    // close socket
	                    socketTest1.close();
	                    LOGGER.debug("TXresponse parsed = " + txresp.toString());
	                    this.addMessage("TXresponse parsed = " + txresp.toString());
	                    this.publishProgress(new DefaultAsyncProgress(1.0, "TXresponse parsed;"));
	                    
	                    // Now we are ready to start with algorithm
	                    // At first determine peer IP and port
	                    TXPeer[] peers = txresp.getPeers();
	                    if (peers.length!=2) {
	                    	throw new RuntimeException("Transaction has to have exactly 2 peers;");
	                    }
	                    
	                    // master?
	                    cfg.getCfg().setMaster(true);
	                    
	                    int peerIdx=0;
	                    if (peers[0].getId().equalsIgnoreCase(myId)) peerIdx++;
	                    cfg.getCfg().setPeerIP(peers[peerIdx].getIP());
	                    cfg.getCfg().setPeerPort(peers[peerIdx].getPort());
	                    if (myId.compareTo(peers[peerIdx].getId())>0) cfg.getCfg().setMaster(false);
	                    
	                    LOGGER.debug("Remote peer determined as: " + peers[peerIdx].toString() + "; master=" + (!cfg.getCfg().isMaster()));
	                    this.addMessage("Remote peer determined as: " + peers[peerIdx].toString() + "; master=" + (!cfg.getCfg().isMaster()));
	                    
	                    //
	                    // Transaction was initiated successfully
	                    //
	                    txConnectedTime = System.currentTimeMillis();
	            		this.firstMessageReceived = 0;
	            		this.receivedMessages=0;
	            		this.recvPorts.clear();
	                    
	            		runningTest = true;
	                    this.startAlg(cfg);
	                    runningTest = false;
	                    
	                    // harvest collected data and write it to the file
	                    BenchRecord rec      = new BenchRecord();
	                    rec.delay            = testParam.curDelayIdx;
	                    rec.portCount        = testParam.curPortIdx;
	                    rec.testNumber       = testParam.curTestIdx;
	                    rec.firstReceivedMsg = firstMessageReceived;
	                    rec.receivedMsgs     = receivedMessages;
	                    rec.timeStarted      = txStartTime;
	                    rec.timeTXCompleted  = txConnectedTime;
	                    rec.txid             = "NA";
	                    rec.localIP          = localIP;
	                    
	                    rec.publicIP         = peers[peerIdx==0 ? 1:0].getIP();
	                    rec.publicPort       = peers[peerIdx==0 ? 1:0].getPort();
	                    
	                    rec.remoteIP         = peers[peerIdx].getIP();
	                    rec.remotePort       = peers[peerIdx].getPort();
	                    rec.remoteAnswers    = "";
	                    
	                    for(Integer rc : recvPorts){
	                    	rec.remoteAnswers += rc + ",";
	                    }
	                    
	                    fileWriter.append(rec.toRec() + "\n");
	                    fileWriter.flush();
	                    
	                    logMsg = "Current test finished; recvMessages=["+receivedMessages+"]";
	                    LOGGER.debug(logMsg);
	                    this.addMessage(logMsg);
	                    
	                    try {
	                    	Thread.sleep(postTestPause);
	                    } catch(InterruptedException e){
	                    	LOGGER.warn("Something interrupted waiting after test is done", e);
	                    }
	                    
	                    // stop while cycle -> next scan iteration
	                    break;
		            } catch (SocketTimeoutException ste) {
		                // node is not capable of udp communication
		                LOGGER.error("Transaction timeouted");
		                this.publishProgress(new DefaultAsyncProgress(1.0, "Transaction timeouted"));
		                
		                return null;
		            } catch (Exception e){
		            	LOGGER.error("Unknown exception", e);
		            	this.addMessage("Unknown exception: " + e.getMessage());
		            	
		            	return null;
		            } finally {
		                try {
		                    socketTest1.close();
		                } catch(Exception e){
		                    
		                }
		            }
		        } // end of while true
	        } // end of for(curTestID...
	        
	        this.publishProgress(new DefaultAsyncProgress(1.0, "Done"));
			Log.i(TAG, "Finished properly");
		} catch (UnknownHostException e1) {
			Log.e(TAG, "Unknown host excepion", e1);
		} catch (RuntimeException re){
			Log.e(TAG, "RException", re);
			return re;
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);
			return e;
		}
		
		LOGGER.debug("Benchmark task finishing");
		return null;
	}
	
	@Override
	public void onNewMessageReceived(ReceivedMessage msg) {
		// Message was received on the port !!! Store this somewhere
		if (runningTest==false){
			LOGGER.debug("Message was received but it seems test is not running, will just ignore that");
			return;
		}
		
		if (this.receivedMessages==0) this.firstMessageReceived = System.currentTimeMillis();
		this.receivedMessages+=1;
		this.recvPorts.add(msg.getSourcePort());
	}
	
	protected Exception startAlg(BenchTaskParam arg0){
		this.cfg = arg0;
		this.publishProgress(new DefaultAsyncProgress(1, "Running connection algorithm"));
		
		try {	        
	        boolean master = cfg.getCfg().isMaster();
	        int startPort = cfg.getCfg().getPeerPort();
	        String peerIP = cfg.getCfg().getPeerIP();
	        int currentPort = startPort + 1;  // remember this was the port used to contact introducer, add 1
	        int N = cfg.getCfg().getN();
	        String txtMsg = "HelloWorld! my local: " + localIP + "; public=" + cfg.getCfg().getPublicIP() + "\n" ;
	        byte[] dataMsg = txtMsg.getBytes("UTF-8");
	        
	        LOGGER.debug("Going to start sending packets to: [" + cfg.getCfg().getPeerIP() + ":"+startPort+"] as a " + (master ? "master" : "slave"));
	        this.addMessage("Going to start sending packets to: [" + cfg.getCfg().getPeerIP() + ":"+startPort+"] as a " + (master ? "master" : "slave"));
	        
	        // do until user cancels it
	        this.publishProgress(new DefaultAsyncProgress(0.05, "Starting packet sending", "Starting packet sending"));
	        for(int iter=0; this.wasCancelled()==false; iter++){
	        	currentPort = startPort;
	        	
	        	// prepare messages to send
	        	List<Message2SendParc> lst = new ArrayList<Message2SendParc>(2*N);
	        	for(int i=0; i<N; i++){
	        		for (int j=0; j<2; j++){
		        		if (master)
		        			currentPort = startPort + i*3+j;
		        		else
		        			currentPort = startPort + (i*2)*2+j;
	        		
		        		// enqueue message to send, send same packet multiple times, increase penetration in case some 
		        		// packet got lost
		        		Message2SendParc m2s = new Message2SendParc();
		        		m2s.setSourceIP(peerIP);
		        		m2s.setSourcePort(currentPort);
		        		m2s.setMessage(dataMsg);
		        		lst.add(m2s);
		        		lst.add(m2s);
	        		}
	        		
		        	//cfg.getCfg().getApi().sendMessage(peerIP, currentPort, dataMsg);
		        	//cfg.getCfg().getApi().sendMessage(peerIP, currentPort, dataMsg);
		        	
		        	// if iter == 0, repeat this once more?
		        	if (iter==0 && i==(N-1)){
		        		iter+=1;
		        		i=-1;
		        	}
	        	}
	        	
	        	// send messages; packet as a bulk
	        	cfg.getCfg().getApi().sendMessageList(peerIP, lst);
	        	
	        	// GUI update moved here since it slows down port spraying
	        	if (iter==1){
	        		for(int i=0; i<N; i++){
		        		if (master)
		        			currentPort = startPort + i;
		        		else
		        			currentPort = startPort + i*2;
		        		this.addMessage("Sending message to " + peerIP + ":" + currentPort);
		        	}
        		}
	        	
	        	if (this.wasCancelled()){
	        		LOGGER.debug("Canceled, shuting down algorithm");
	        		return null;
	        	}
	        	
	        	try {
	        		Thread.sleep(1000);
	        	} catch(Exception e){
	        		LOGGER.error("Thread sleep interrupted, exiting with connecting");
	        		return null;
	        	}
	        	
	        	long curTime = System.currentTimeMillis();
	        	if ((curTime - txConnectedTime) > traverseTime){
	        		LOGGER.debug("This traverse iteration is over. finished");
	        		
	        		// clear all messages from the senders queue
	        		cfg.getCfg().getApi().clearSenderQueue();
	        		return null;
	        	}
	        }
	        
	        this.publishProgress(new DefaultAsyncProgress(1.0, "Done"));
			Log.i(TAG, "Finished properly");
		} catch (RuntimeException re){
			Log.e(TAG, "RException", re);
			return re;
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);
			return e;
		}
		
		return null;
	}
	
	protected boolean wasCancelled(){
		boolean canceled = this.isCancelled();
		if (canceled==true){
			LOGGER.debug("NAT detect was cancelled");
			this.onCancelled(null);
		}
		
		return canceled;
	}
	
	@Override
	protected void onCancelled() {
		LOGGER.debug("NAT detect task; onCancelled()");
		super.onCancelled();
	}

	/**
	 * onPreExecute(), invoked on the UI thread before the task is executed. 
	 * This step is normally used to setup the task, for instance by showing a progress bar in the user interface.
	 * 
	 * @param f
	 */
	@Override
	protected void onPreExecute() {		
		// setup dialog
		if (dialog!=null){
			dialog.setProgress(0);
			dialog.setMessage("Initialized");
		}
		
		if (callback!=null){
			callback.onTaskUpdate(null, 0);
		}
	}
	
    @Override
	protected void onPostExecute(Exception result) {
    	LOGGER.debug("onPostExecute() Benchmark task");
    	
    	deinitFile();
    	
    	// remove me from the observer
		if (this.frag!=null){
			frag.removeObserver(this);
		}
    	
		// setup dialog
		if (dialog!=null){
			// some exception returned -> problem during process
			if (result!=null){
				dialog.dismiss();
				
				// show error dialog
				showErrorDialog("Problem", "Problem ocurred, probably due to internet connection, please try again later");
			} else {
				dialog.setProgress(dialog.getMax());
				dialog.setMessage("Done");
				dialog.dismiss();
			}
		}
		
		if (callback!=null){
			LOGGER.debug("onPostExecute() NAT detect task - callback");
			callback.onTaskUpdate(null, 2);
		}
	}

	@Override
	protected void onProgressUpdate(DefaultAsyncProgress... values) {
		// setup dialog		
		if (dialog!=null){
			dialog.setProgress((int)(dialog.getMax() * values[0].getPercent()));
			dialog.setMessage(values[0].getMessage());
		}
		
		if (callback!=null){
			if (publicIP!=null)
				callback.setPublicIP(publicIP);
			callback.onTaskUpdate(values[0], 1);
		}
	}

	@Override
	protected void onCancelled(Exception result) {
		if (dialog!=null){
			dialog.cancel();
		}
		
		if (callback!=null){
			LOGGER.debug("NAT task cancelled, -1");
			callback.onTaskUpdate(null, -1);
		}
	}
	
	 public void showErrorDialog(final String title, final String message) {
	    AlertDialog aDialog = new AlertDialog.Builder(this.context)
	    	.setMessage(message)
	    	.setTitle(title)
	        .setNeutralButton("Close", new OnClickListener() {
	          public void onClick(final DialogInterface dialog, final int which) {
	            
	          }
	        }).create();
	    aDialog.setOnKeyListener(this);
	    aDialog.show();
	  }

	  @Override
	  public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
	    if(keyCode == KeyEvent.KEYCODE_BACK){
	      //disable the back button
	    }
	    return true;
	  }
	  

	public ProgressDialog getDialog() {
		return dialog;
	}

	public void setDialog(ProgressDialog dialog) {
		this.dialog = dialog;
	}

	public Context getContext() {
		return context;
	}

	public void setContext(Context context) {
		this.context = context;
	}

	public Resources getResources() {
		return resources;
	}

	public void setResources(Resources resources) {
		this.resources = resources;
	}

	public AsyncTaskListener getCallback() {
		return callback;
	}

	public void setCallback(AsyncTaskListener callback) {
		this.callback = callback;
	}

	@Override
	public void addMessage(String message) {
		this.publishProgress(new DefaultAsyncProgress(0.5, "Update from BenchmarkTask", message));
	}

	public GuiLogger getGuiLogger() {
		return guiLogger;
	}

	public void setGuiLogger(GuiLogger guiLogger) {
		this.guiLogger = guiLogger;
	}

	public ParametersFragment getFrag() {
		return frag;
	}

	public void setFrag(ParametersFragment frag) {
		this.frag = frag;
	}
}

