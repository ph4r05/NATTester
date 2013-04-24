package com.phoenix.nattester;

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

/**
 * Async task for connection making
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class AlgTask extends AsyncTask<TaskAppConfig, DefaultAsyncProgress, Exception> implements OnKeyListener, MessageInterface {
	private static final Logger LOGGER = LoggerFactory.getLogger(AlgTask.class);
	public final static String TAG = "AlgTask";
	
	// where to publish progress
	private ProgressDialog dialog = null;
	// context
	private Context context = null;
	// resources - from strings
	private Resources resources = null;
	private boolean manualCancel=false;
	
	private AsyncTaskListener callback;
	private TaskAppConfig cfg;
	private String publicIP=null;
	
	@Override
	protected Exception doInBackground(TaskAppConfig... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		this.cfg = arg0[0];
		this.publishProgress(new DefaultAsyncProgress(0.05, "Running connection algorithm"));
		
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
	        String txtMsg = "HelloWorld! my local: " + localIP + "\n";
	        byte[] dataMsg = txtMsg.getBytes("UTF-8");
	        LOGGER.debug("Going to start sending packets to: [" + cfg.getPeerIP() + ":"+startPort+"] as a " + (master ? "master" : "slave"));
	        
	        // do until user cancels it
	        this.publishProgress(new DefaultAsyncProgress(0.05, "Starting packet sending", "Starting packet sending"));
	        while(this.isCancelled()==false && this.manualCancel==false){
	        	currentPort = startPort;
	        	for(int i=0; i<N; i++){
	        		if (master)
	        			currentPort = startPort + i;
	        		else
	        			currentPort = startPort + i*2;
	        		
		        	// enqueue message to send, send same packet multiple times
		        	cfg.getApi().sendMessage(cfg.getPeerIP(), currentPort, dataMsg);
		        	cfg.getApi().sendMessage(cfg.getPeerIP(), currentPort, dataMsg);
	        	}
	        	
	        	if (this.isCancelled() || this.manualCancel){
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
				//showErrorDialog("Problem", "Problem ocurred, probably due to internet connection, please try again later");
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
		LOGGER.info("Cancelling algorithm run");
		this.manualCancel=true;
		
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

