package com.phoenix.nattester;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

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
import android.util.Log;
import android.view.KeyEvent;

import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;

import de.javawi.jstun.attribute.ChangeRequest;
import de.javawi.jstun.attribute.ChangedAddress;
import de.javawi.jstun.attribute.ErrorCode;
import de.javawi.jstun.attribute.MappedAddress;
import de.javawi.jstun.attribute.MessageAttribute;
import de.javawi.jstun.header.MessageHeader;

/**
 * Async task for obaining public IP address	
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class GetPublicIPTask extends AsyncTask<TaskAppConfig, DefaultAsyncProgress, Exception> implements OnKeyListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(GetPublicIPTask.class);
	public final static String TAG = "ClistFetchTask";
	
	// where to publish progress
	private ProgressDialog dialog = null;
	// context
	private Context context = null;
	// resources - from strings
	private Resources resources = null;
	
	private AsyncTaskListener callback;
	private TaskAppConfig cfg;
	private String publicIP=null;
	
	@Override
	protected Exception doInBackground(TaskAppConfig... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		this.cfg = arg0[0];
		int timeoutInitValue = 300;
		int localPort = 34567;
		this.publishProgress(new DefaultAsyncProgress(0.05, "Sending STUN request"));
		
		try {
			// UDP datagram
			DatagramSocket socketTest1 = null;
			String address2connect = cfg.getStunServer();
	        InetAddress address2connectInet;
			address2connectInet = InetAddress.getByName(cfg.getStunServer());
	
	        // here is stored log from scan - to detect address/port sensitivity
	        int timeSinceFirstTransmission = 0;
	        int timeout = timeoutInitValue;
        
	        // obtain private IP
	        String localIP = Utils.getIPAddress(true);
	        InetAddress iaddress = InetAddress.getByName(localIP);
	        
	        LOGGER.debug("Local IP address obtained: " + localIP);
	        this.publishProgress(new DefaultAsyncProgress(0.05, "Local IP obtained", "LocalIP: " + localIP));
	        
	        // this while represents simple packet request reply cycle. (problem may occurr - resending)
	        while (true) {
	            try {
	                // build simple STUN request on stun server from my interface and static local port
	                socketTest1 = new DatagramSocket(new InetSocketAddress(iaddress, localPort));
	                socketTest1.setReuseAddress(true);
	                socketTest1.connect(address2connectInet, cfg.getStunPort());
	                socketTest1.setSoTimeout(timeout);
	
	                MessageHeader sendMH = new MessageHeader(MessageHeader.MessageHeaderType.BindingRequest);
	                sendMH.generateTransactionID();
	
	                ChangeRequest changeRequest = new ChangeRequest();
	                sendMH.addMessageAttribute(changeRequest);
	
	                byte[] data = sendMH.getBytes();
	                DatagramPacket send = new DatagramPacket(data, data.length);
	                socketTest1.send(send);
	                LOGGER.debug("Test portDelta: Binding Request sent to: " + address2connect + ":" + cfg.getStunPort());
	
	                // wait for our packet
	                MessageHeader receiveMH = new MessageHeader();
	                while (!(receiveMH.equalTransactionID(sendMH))) {
	                    DatagramPacket receive = new DatagramPacket(new byte[200], 200);
	                    socketTest1.receive(receive);
	                    receiveMH = MessageHeader.parseHeader(receive.getData());
	                    receiveMH.parseAttributes(receive.getData());
	                }
	
	                // packet arived, get my address (MAPPED-ADDRESS)
	                MappedAddress ma = (MappedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.MappedAddress);
	                ChangedAddress ca = (ChangedAddress) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ChangedAddress);
	                ErrorCode ec = (ErrorCode) receiveMH.getMessageAttribute(MessageAttribute.MessageAttributeType.ErrorCode);
	                if (ec != null) {
	                    LOGGER.error("Message header contains an Errorcode message attribute.");
	                    return null;
	                }
	                if (ma == null || ca == null) {
	                    LOGGER.error("Response does not contain a Mapped Address or Changed Address message attribute.");
	                    return null;
	                } else {
	                    // close socket
	                    socketTest1.close();
	                    String path = "["+localIP+":" + localPort + "]"
	                            + "<--|"+ma.getAddress().toString()+":"+ma.getPort()+"|"
	                            + "<--{"+address2connect+":"+cfg.getStunPort()+"}";
	                    
	                    this.publicIP = ma.getAddress().toString();
	                    this.publishProgress(new DefaultAsyncProgress(0.05, "Public IP obtained", path + "\n"));
	                    this.cfg.setPublicIP(ma.getAddress().toString());
	                    LOGGER.debug("Test portDelta: received reply: " + path);
	                    // stop while cycle -> next scan iteration
	                    break;
	                }
	            } catch (SocketTimeoutException ste) {
	                if (timeSinceFirstTransmission < 7900) {
	                    LOGGER.warn("Test portDelta: Socket timeout while receiving the response.");
	                    timeSinceFirstTransmission += timeout;
	                    int timeoutAddValue = (timeSinceFirstTransmission * 2);
	                    if (timeoutAddValue > 1600) {
	                        timeoutAddValue = 1600;
	                    }
	                    timeout = timeoutAddValue;
	                } else {
	                    // node is not capable of udp communication
	                    LOGGER.error("Test portDelta: Socket timeout while receiving the response. Maximum retry limit exceed. Give up.");
	                    return null;
	                }
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
		
		return null;
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
}

