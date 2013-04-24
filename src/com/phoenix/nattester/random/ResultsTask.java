package com.phoenix.nattester.random;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.concurrent.ConcurrentLinkedQueue;

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
import com.phoenix.nattester.MessageInterface;
import com.phoenix.nattester.TaskAppConfig;
import com.phoenix.nattester.TaskCancelInfo;

/**
 * Async task for NAT detection	
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class ResultsTask extends AsyncTask<TaskAppConfig, DefaultAsyncProgress, Exception> 
	implements OnKeyListener, MessageInterface, TaskCancelInfo {
	private static final Logger LOGGER = LoggerFactory.getLogger(ResultsTask.class);
	public final static String TAG = "ResultsTask";
	
	// where to publish progress
	private ProgressDialog dialog = null;
	// context
	private Context context = null;
	// resources - from strings
	private Resources resources = null;
	
	private AsyncTaskListener callback;
	@SuppressWarnings("unused")
	private TaskAppConfig cfg;
	private String publicIP=null;
	
	// primary attribute here - from this queue will data be dumped to a file
	private ConcurrentLinkedQueue<ProbeTaskReturn> queue = null;
	FileWriter fileWriter = null;
	
	@Override
	protected Exception doInBackground(TaskAppConfig... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		this.cfg = arg0[0];
		this.publishProgress(new DefaultAsyncProgress(0.05, "Initializing"));
		
		// Get the directory for the user's public pictures directory. 
        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "random-"+System.currentTimeMillis()+".txt");
        LOGGER.debug("File is: " + file.getAbsolutePath() + "; is file=" + file.isFile());
		try {
			fileWriter = new FileWriter(file, true);
		} catch (FileNotFoundException e) {
			LOGGER.error("File not found exception", e);
			return e;
		} catch (Exception e){
			LOGGER.error("Generic exception", e);
			return e;
		}
		
		// main while loop - dumping to the file
		Exception toReturn = null;
		while(this.queue.isEmpty()==false || this.wasCancelled()==false){
			try {
		        ProbeTaskReturn e = this.queue.poll();
		        if (e==null) continue;
				
		        // write elem to the file
		        StringBuilder sb = new StringBuilder();
		        sb.append(e.getInitTime()).append(";")
		          .append(e.getSendTime()).append(";")
		          .append(e.getRecvTime()).append(";")
		          .append(e.getLocalAddress()).append(";")
		          .append(e.getSrcPort()).append(";")
		          .append(e.getDstPort()).append(";")
		          .append(e.getMappedAddress()).append(";")
		          .append(e.getMappedPort()).append(";")
		          .append(e.getSendRetryCnt()).append(";")
		          .append(e.getRecvRetryCnt()).append(";")
		          .append(e.isFinished()).append(";")
		          .append(e.isError()).append(";")
		          .append(e.getErrCode()).append(";")
		          .append(e.getErrReason()).append("\n");
		        fileWriter.write(sb.toString());
		        e=null;
				
				if (this.wasCancelled()) break;
				Thread.sleep(250);
			} catch (InterruptedException e1) {
				Log.e(TAG, "Interrupted exception");
				toReturn=e1;
				break;
			} catch (Exception e) {
				Log.e(TAG, "Exception", e);
				toReturn=e;
				break;
			}
		} 
		
		// flush & close
		try {
			fileWriter.flush();
			fileWriter.close();
		} catch(Exception e){
			Log.e(TAG, "Exception during file writer closing", e);
		}

		this.publishProgress(new DefaultAsyncProgress(1.0, "Done;"));
		Log.i(TAG, "Finished properly");
		
		return toReturn;
	}
	
	/**
	 * Adds new record to write queue
	 * @param e
	 */
	public void addResult(ProbeTaskReturn e){
		if (this.queue==null) throw new NullPointerException("Queue is not initiated");
		this.queue.add(e);
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
		queue = new ConcurrentLinkedQueue<ProbeTaskReturn>();
		
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
}

