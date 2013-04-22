package com.phoenix.nattester;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.R.bool;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.Log;
import android.view.KeyEvent;

import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.tx.TXPeer;
import com.phoenix.nattester.tx.TXResponse;

/**
 * Async task for NAT detection	
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class TraverseTask extends AsyncTask<TaskAppConfig, DefaultAsyncProgress, Exception> implements OnKeyListener, MessageInterface {
	private static final Logger LOGGER = LoggerFactory.getLogger(GetPublicIPTask.class);
	public final static String TAG = "TraverseTask";
	
	// where to publish progress
	private ProgressDialog dialog = null;
	// context
	private Context context = null;
	// resources - from strings
	private Resources resources = null;
	
	private AsyncTaskListener callback;
	private GuiLogger guiLogger;
	private TaskAppConfig cfg;
	private String publicIP=null;
	
	@Override
	protected Exception doInBackground(TaskAppConfig... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		this.cfg = arg0[0];
		int timeoutInitValue = 1000*60;	// transaction timeout value in ms - one minute
		int localPort = 34567;
		this.publishProgress(new DefaultAsyncProgress(0.05, "Running tests"));
		String logMsg="";
		
		try {
			// UDP datagram
			DatagramSocket socketTest1 = null;
			String address2connect = cfg.getStunServer();
	        InetAddress address2connectInet;
			address2connectInet = InetAddress.getByName(cfg.getTxServer());
        
	        // obtain private IP
	        String localIP = Utils.getIPAddress(true);
	        InetAddress iaddress = InetAddress.getByName(localIP);
	        LOGGER.debug("Local IP address obtained: " + localIP);
	        
	        //
	        // Phase 1 - start transaction here
	        //
	        
	        // here is stored log from scan - to detect address/port sensitivity
	        int timeSinceFirstTransmission = 0;
	        int timeout = 300;
	        long txStartTime = System.currentTimeMillis();
	        
	        LOGGER.debug("Local IP address obtained: " + localIP);
	        this.publishProgress(new DefaultAsyncProgress(0.05, "Local IP obtained", "LocalIP: " + localIP));
	        
	        // this while represents simple packet request reply cycle. (problem may occurr - resending)
	        while (true) {
	            try {
	            	if (wasCancelled()) return null;
	            	
	                // build simple STUN request on stun server from my interface and static local port
	                socketTest1 = new DatagramSocket(new InetSocketAddress(iaddress, localPort));
	                socketTest1.setReuseAddress(true);
	                socketTest1.connect(address2connectInet, cfg.getTxServerPort());
	                socketTest1.setSoTimeout(timeout);
	
	                // build transaction request
	                String myId = localIP+"-"+cfg.getPublicIP()+"-"+cfg.getTxId();
	                String txreq = "txbegin|"+cfg.getTxName()+"|"+myId+"|default";
	                byte[] data = txreq.getBytes("UTF-8");
	                DatagramPacket send = new DatagramPacket(data, data.length);
	                
	                logMsg = "TXBegin request: ["+txreq+"] sent to server " + address2connect + ":" + cfg.getTxServerPort();
	                LOGGER.debug(logMsg);
	                this.addMessage(logMsg);
	                this.publishProgress(new DefaultAsyncProgress(1.0, "Initiating transaction"));
	                socketTest1.send(send);
	                
	                
	                // timeouted waiting for transaction
	                String txanswer = "";
	                while(true){
	                	try {
	                		if (wasCancelled()) return null;
	                		
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
		                	if (txCurTime > (txStartTime + 1000*60)){
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
                    cfg.setMaster(true);
                    
                    int peerIdx=0;
                    if (peers[0].getId().equalsIgnoreCase(myId)) peerIdx++;
                    cfg.setPeerIP(peers[peerIdx].getIP());
                    cfg.setPeerPort(peers[peerIdx].getPort());
                    if (myId.compareTo(peers[peerIdx].getId())>0) cfg.setMaster(false);
                    
                    LOGGER.debug("Remote peer determined as: " + peers[peerIdx].toString() + "; master=" + (!cfg.isMaster()));
                    this.addMessage("Remote peer determined as: " + peers[peerIdx].toString() + "; master=" + (!cfg.isMaster()));
                    this.publishProgress(new DefaultAsyncProgress(1.0, "Starting connectivity alogrithm"));
                    this.startAlg(cfg);
                    
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
	        }
	        
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
		
		LOGGER.debug("NAT task finishing");
		return null;
	}
	
	protected Exception startAlg(TaskAppConfig arg0){
		this.cfg = arg0;
		this.publishProgress(new DefaultAsyncProgress(1, "Running connection algorithm"));
		
		try {        
	        // obtain private IP
	        String localIP = Utils.getIPAddress(true);
	        LOGGER.debug("Local IP address obtained: " + localIP);
	        
	        if (cfg.getApi()==null){
	        	LOGGER.error("Invalid Service API connection");
	    		this.publishProgress(new DefaultAsyncProgress(0.05, "Invalid Service API connection", "Invalid Service API connection"));
	    		return null;
	        }
	        
	        boolean master = cfg.isMaster();
	        int startPort = cfg.getPeerPort();
	        int currentPort = startPort;
	        int N = cfg.getN();
	        String txtMsg = "HelloWorld! my local: " + localIP + "; public=" + cfg.getPublicIP() + "\n" ;
	        byte[] dataMsg = txtMsg.getBytes("UTF-8");
	        
	        LOGGER.debug("Going to start sending packets to: [" + cfg.getPeerIP() + ":"+startPort+"] as a " + (master ? "master" : "slave"));
	        this.addMessage("Going to start sending packets to: [" + cfg.getPeerIP() + ":"+startPort+"] as a " + (master ? "master" : "slave"));
	        
	        // do until user cancels it
	        this.publishProgress(new DefaultAsyncProgress(0.05, "Starting packet sending", "Starting packet sending"));
	        for(int iter=0; this.wasCancelled()==false; iter++){
	        	currentPort = startPort;
	        	for(int i=0; i<N; i++){
	        		if (master)
	        			currentPort = startPort + i;
	        		else
	        			currentPort = startPort + i*2;
	        		
	        		if (iter==0){
	        			this.addMessage("Sending message to " + cfg.getPeerIP() + ":" + currentPort);
	        		}
	        		
		        	// enqueue message to send, send same packet multiple times
		        	cfg.getApi().sendMessage(cfg.getPeerIP(), currentPort, dataMsg);
		        	cfg.getApi().sendMessage(cfg.getPeerIP(), currentPort, dataMsg);
	        	}
	        	
	        	if (this.wasCancelled()){
	        		LOGGER.debug("Canceled, shuting down algorithm");
	        		return null;
	        	}
	        	
	        	try {
	        		Thread.sleep(10000);
	        	} catch(Exception e){
	        		LOGGER.error("Thread sleep interrupted, exiting with connecting");
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
    	LOGGER.debug("onPostExecute() NAT detect task");
    	
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
		this.publishProgress(new DefaultAsyncProgress(0.5, "Update from TraverseTask", message));
	}

	public GuiLogger getGuiLogger() {
		return guiLogger;
	}

	public void setGuiLogger(GuiLogger guiLogger) {
		this.guiLogger = guiLogger;
	}
}

