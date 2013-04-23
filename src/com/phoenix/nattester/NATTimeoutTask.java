package com.phoenix.nattester;

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

import de.javawi.jstun.test.BindingLifetimeTest;
import de.javawi.jstun.test.ExternalPortBindingLifetimeTest;

/**
 * Async task for NAT detection	
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class NATTimeoutTask extends AsyncTask<TaskAppConfigNATTimeout, DefaultAsyncProgress, Exception> 
	implements OnKeyListener, MessageInterface, TaskCancelInfo {
	private static final Logger LOGGER = LoggerFactory.getLogger(NATTimeoutTask.class);
	public final static String TAG = "NATTimeoutTask";
	
	// where to publish progress
	private ProgressDialog dialog = null;
	// context
	private Context context = null;
	// resources - from strings
	private Resources resources = null;
	
	private AsyncTaskListener callback;
	private GuiLogger guiLogger;
	private TaskAppConfigNATTimeout cfg;
	private String publicIP=null;
	
	private ExternalPortBindingLifetimeTest dt;
	
	@Override
	protected Exception doInBackground(TaskAppConfigNATTimeout... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		this.cfg = arg0[0];
		this.publishProgress(new DefaultAsyncProgress(0.05, "Initializing"));
		
		try {
	        dt = new ExternalPortBindingLifetimeTest(cfg.getCfg().getStunServer(), cfg.getCfg().getStunPort(), 5060);
	        dt.setCallback(this);
	        dt.setCancelInfo(this);

			this.publishProgress(new DefaultAsyncProgress(0.2, "Binding life time test"));
			if (this.wasCancelled()) return null;
			
			if (cfg.getTestType()==1){
				dt.setUpperBinarySearchLifetime(60000);
				dt.test();
			} else {
				dt.setUpperBinarySearchLifetime(280000);
				dt.test2();
			}
			
	        this.publishProgress(new DefaultAsyncProgress(1.0, "Done; lifetime=" + dt.getLifetime()));
			Log.i(TAG, "Finished properly");
			
			if (this.wasCancelled()) return null;
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
	
	public synchronized boolean wasCancelled(){
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
		if (this.dt!=null){
			this.dt.cancel();
		}
		
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
		this.publishProgress(new DefaultAsyncProgress(0.5, "Update from NATdetect", message));
	}

	public GuiLogger getGuiLogger() {
		return guiLogger;
	}

	public void setGuiLogger(GuiLogger guiLogger) {
		this.guiLogger = guiLogger;
	}
}

