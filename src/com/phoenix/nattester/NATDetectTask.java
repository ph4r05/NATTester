package com.phoenix.nattester;

import java.net.DatagramSocket;
import java.net.InetAddress;
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

import de.javawi.jstun.test.DiscoveryInfo;
import de.javawi.jstun.test.DiscoveryTest;

/**
 * Async task for NAT detection	
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class NATDetectTask extends AsyncTask<TaskAppConfig, DefaultAsyncProgress, Exception> implements OnKeyListener, MessageInterface {
	private static final Logger LOGGER = LoggerFactory.getLogger(GetPublicIPTask.class);
	public final static String TAG = "NATDetectTask";
	
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
		this.publishProgress(new DefaultAsyncProgress(0.05, "Running tests"));
		
		try {
			// UDP datagram
			DatagramSocket socketTest1 = null;
			String address2connect = cfg.getStunServer();
	        InetAddress address2connectInet;
			address2connectInet = InetAddress.getByName(cfg.getStunServer());
        
	        // obtain private IP
	        String localIP = Utils.getIPAddress(true);
	        InetAddress iaddress = InetAddress.getByName(localIP);
	        LOGGER.debug("Local IP address obtained: " + localIP);
	        
	        DiscoveryTest dt = new DiscoveryTest(iaddress, address2connect, cfg.getStunPort());
	        dt.setCallback(this);
	        dt.setTimeoutInitValue(timeoutInitValue);
	        dt.setMa(null);
	        dt.setCa(null);
	        dt.setNodeNatted(true);
	        dt.setSocketTest1(null);
			dt.setDi(new DiscoveryInfo(iaddress));

			this.publishProgress(new DefaultAsyncProgress(0.2, "Test 1: isnodeNAT-ed?"));
			if (dt.test1()) {
				if (this.isCancelled()) return null;
				this.publishProgress(new DefaultAsyncProgress(0.3, "Test 2: address/port (CONE) restricted?"));
				
				if (dt.test2()) {
					if (this.isCancelled()) return null;
					this.publishProgress(new DefaultAsyncProgress(0.4, "Test 1 redo: restore changed IP"));
					
					if (dt.test1Redo()) {
						if (this.isCancelled()) return null;
						this.publishProgress(new DefaultAsyncProgress(0.5, "Test 3: is port restricted?"));
						
						dt.test3();
					}
				}
			}
			
	        try {
	            if (socketTest1!=null 
	            		&& (socketTest1.isBound() || socketTest1.isConnected()) 
	            		&& socketTest1.isClosed()) 
	            	socketTest1.close();
	        } catch(Exception e){
	            ;
	        }
	        
	        // detect symmetric NAT IP/port sensitivity
	        if (this.isCancelled()) return null;
	        this.publishProgress(new DefaultAsyncProgress(0.6, "Test portDeta: switchIP, fast"));
	        dt.testSymmetricPortDelta(3480, 10, 34567, 0, true);
	        
	        // finally only port switching
	        if (this.isCancelled()) return null;
	        this.publishProgress(new DefaultAsyncProgress(0.7, "Test portDeta: sameIP, fast"));
	        dt.testSymmetricPortDelta(3500, 10, 34567, 100, false);
	        
	        // once again with 1 second pause between scans
	        if (this.isCancelled()) return null;
	        this.publishProgress(new DefaultAsyncProgress(0.8, "Test portDeta: switch IP, long"));
	        dt.testSymmetricPortDelta(3490, 10, 34567, 1000, true);
	        
	        DiscoveryInfo di = dt.test();
	        Log.i(TAG, "DiscoveryInfo: " + di.toString());
	        
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

	@Override
	public void addMessage(String message) {
		this.publishProgress(new DefaultAsyncProgress(0.5, "Update from NATdetect", message));
	}
}

