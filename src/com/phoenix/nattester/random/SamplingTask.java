package com.phoenix.nattester.random;

import java.net.InetAddress;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

import com.phoenix.nattester.DefaultAsyncProgress;
import com.phoenix.nattester.MessageInterface;
import com.phoenix.nattester.TaskCancelInfo;
import com.phoenix.nattester.Utils;
import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.service.ServerService;

/**
 * Task to sample NAT usage. 	
 * 
 * @author ph4r05
 * docs: http://developer.android.com/reference/android/os/AsyncTask.html
 */
public class SamplingTask extends AsyncTask<RandomTaskParam, DefaultAsyncProgress, Exception> 
	implements OnKeyListener, MessageInterface, TaskCancelInfo {
	private static final Logger LOGGER = LoggerFactory.getLogger(RandomTask.class);
	public final static String TAG = "SamplingTask";
	
	// where to publish progress
	private ProgressDialog dialog = null;
	// context
	private Context context = null;
	// resources - from strings
	private Resources resources = null;
	
	private AsyncTaskListener callback;
	private RandomTaskParam cfg;
	private String publicIP=null;
	
	private boolean cancelledTriggered=false;
	private ResultsTask rtask;
	private Probe probe;
	
	@Override
	protected Exception doInBackground(RandomTaskParam... arg0) {
		if (arg0.length==0){
			throw new IllegalArgumentException("Empty configuration");
		}
		
		this.cfg = arg0[0];
		this.cancelledTriggered = false;
		this.publishProgress(new DefaultAsyncProgress(0.05, "Initializing"));
		
		try {
			// local IP - stored in results file
			String localIP = null;
			boolean dump2file = !(cfg.isNoRecv() || cfg.isNoStun());
			
	        // create result task - for storing results to file
			probe = new Probe();
			rtask = new ResultsTask();
			rtask.setCallback(this.callback);
			rtask.setContext(this.context);
			rtask.setResources(this.resources);
			if (dump2file) executeAsyncTask(rtask, cfg.getCfg());
			this.publishProgress(new DefaultAsyncProgress(0.05, "Rtask started, scanning..."));
			
			// here create scanning probes, in this design, we have only 1 scanning probe, us
			// TODO: add pool of scanning probes, 50 probes scanning each different port.
			// For now we have simple design.
			ProbeTaskParam p = new ProbeTaskParam();
			p.setCfg(cfg.getCfg());
			p.setStunAddressCached(InetAddress.getByName(cfg.getCfg().getStunServer()));
			p.setSocketTimeout(500);
			p.setNoRecv(cfg.isNoRecv());
			p.setNoStun(cfg.isNoStun());
			
			long iterCount = 0;
			long lastDump = 0;
			int pause = cfg.getPause();
			int numports = cfg.getStunPorts();
			
			Random rnd = new Random(System.currentTimeMillis());
			int stunPortIdx = 5 + (rnd.nextInt() % (numports > 2 ? (numports/2) : numports)); // initialize with offset 5 (from getpublic requests and previous...) + random - eliminate previous runs effects
			
			while(this.wasCancelled()==false){
				// cycle over STUN ports to eliminate already opened ports to STUN server - we would like to
				// avoid using already opened ports and thus doing side effect - keep alive on them
				stunPortIdx = (stunPortIdx + 1) % numports;
				int stunPort = (cfg.getCfg().getStunPort() + stunPortIdx) % 65536;
				localIP = Utils.getIPAddress(true);
				
				this.publishProgress(new DefaultAsyncProgress(0.5, "New cycle of scan, stunPort=" + stunPort, "New cycle of scan, stunPort=" + stunPort + "; localIP=" + localIP));
				
				// iterate over all source ports here
				p.setExtPort(stunPort);
				for(int srcPort=1025; srcPort<=65535; srcPort++, iterCount++){
					if (srcPort==ServerService.srvPort) continue;
					if (this.wasCancelled()) break;
					p.setIntPort(srcPort);
					
					if (pause>0){
						Thread.sleep(pause);
					}
					
					ProbeTaskReturn tr = probe.probeScan(p);
					tr.setLocalAddress(localIP);
					if (dump2file) rtask.addResult(tr);
					
					// do dump to GUI?
					boolean dump2GUI=false;
					if (cfg.isNoRecv()){
						if (iterCount > 50){
							iterCount=0;
							long curtime = System.currentTimeMillis();
							dump2GUI = (curtime - lastDump) >= 3000;
						}
					}
					
					if (cfg.isNoRecv() == false || (cfg.isNoRecv() && dump2GUI)){
						lastDump = System.currentTimeMillis();
						this.publishProgress(new DefaultAsyncProgress(0.5, "SrcPort="+srcPort+"; stunPort=" + stunPort, 
							"localIP:port="+localIP+":"+srcPort
							+"; stunPort=" + stunPort 
							+"; mappedPort=" + tr.getMappedPort()
							+"; error=" + tr.isError()
							));
					}
					
					if (!dump2file) tr=null;
				}
			}		
			
	        this.publishProgress(new DefaultAsyncProgress(1.0, "Done; ", "Random task stopped"));
			Log.i(TAG, "Finished properly");
		} catch (Exception e) {
			Log.e(TAG, "Exception", e);
			return e;
		}
		
		// if here, cancel task
		rtask.cancel(false);
		
		LOGGER.debug("RandomTask finished");
		return null;
	}
	
	 @TargetApi(11)
	 static public <T> void executeAsyncTask(AsyncTask<T, ?, ?> task, T... params) {
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
	      task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
	    }
	    else {
	      task.execute(params);
	    }
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
		if (cancelledTriggered==false){
			cancelledTriggered=true;
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
}
