package com.phoenix.nattester;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.MainFragmentActivity.ViewPagerVisibilityListener;
import com.phoenix.nattester.service.IServerService;

public class ParametersFragment extends SherlockFragment implements AsyncTaskListener, ViewPagerVisibilityListener {
	  private static final Logger LOGGER = LoggerFactory.getLogger(ParametersFragment.class);
	  public final static String TAG = "ParFragment";
	  
	  protected Button btnGetPublicIP;
	  protected Button btnNATDetect;
	  protected Button btnAbort;
	  protected Button btnAlg;
	  protected CheckBox checkMaster;
	  protected EditText ePublicIP;
	  protected EditText eTxName;
	  protected EditText eTxServer;
	  protected EditText eN;
	  protected ProgressBar pbar;
	  protected TextView pmsg;
	  private InternalProgress iproc;

	  // static application config
	  final TaskAppConfig cfg = new TaskAppConfig(); 
		
	  public final void updateCfgFromUI(){
			try {
				cfg.setMaster(checkMaster.isChecked());
				cfg.setN(Integer.parseInt(eN.getText().toString()));
				
				cfg.setTxName(eTxName.getText().toString());
				cfg.setTxServer(eTxServer.getText().toString());
				//cfg.setPeerIP(ePeerIP.getText().toString());
				//cfg.setPeerPort(Integer.parseInt(ePeerPort.getText().toString()));
				
				cfg.setPublicIP(ePublicIP.getText().toString());
				cfg.setStunPort(3478);
				cfg.setStunServer("89.29.122.60");
				cfg.setUpdateCallback(this);
			} catch(Exception e){
				LOGGER.error("Problem during fetching app config", e);
			}
		}
	  @Override
	  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    // Inflate the layout for this fragment
	    View view = inflater.inflate(R.layout.prefs_fragment, container, false);
	    
	    
	    return view;
	  }
	  
	  @Override
	  public void onAttach(Activity activity) {
	  	  super.onAttach(activity);
	  }
	
	  @Override
	  public void onDetach() {
		  super.onDetach();
		  LOGGER.debug("prefsDetached");
	  }
	
	@Override
	  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		  super.onCreateOptionsMenu(menu, inflater);
	  }
	
	  @Override
	  public void onPrepareOptionsMenu(Menu menu) {
		  super.onPrepareOptionsMenu(menu);
	  }
	
	  @Override
	  public boolean onOptionsItemSelected(MenuItem item) {
		  return super.onOptionsItemSelected(item);
	  }
	
	  @Override
	  public void onViewCreated(View view, Bundle savedInstanceState) {
		  super.onViewCreated(view, savedInstanceState);
		  LOGGER.debug("OnViewCreated - logger");
		
		  // initialize controls
	      btnGetPublicIP = (Button) view.findViewById(R.id.btnGetPublicIP);
		  btnNATDetect = (Button) view.findViewById(R.id.btnNATDetect);
		  btnAlg = (Button) view.findViewById(R.id.btnAlg);
		  btnAbort = (Button) view.findViewById(R.id.btnAbort);
		  checkMaster = (CheckBox) view.findViewById(R.id.checkMaster);
		  ePublicIP = (EditText) view.findViewById(R.id.txtPublic);
		  eTxName = (EditText) view.findViewById(R.id.txtTXName);
		  eTxServer = (EditText) view.findViewById(R.id.txtTXServer);
		  eN = (EditText) view.findViewById(R.id.txtN);
		  pbar = (ProgressBar) view.findViewById(R.id.progressBar1);
		  pmsg = (TextView) view.findViewById(R.id.textVMessage);
		  pbar.setIndeterminate(false);
		  
		  iproc = new InternalProgress();
		  		
		
        // internal process canceller
        btnAbort.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				iproc.cancel();
			}
		});
        
        /** INITIALIZE BUTTON; get public IP **/
        btnGetPublicIP.setOnClickListener(new PublicIPistener());
        
        /** INITIALIZE BUTTON; NAT detect */
        btnNATDetect.setOnClickListener(new NATClickListener());
        
        /** INITIALIZE BUTTON; Traverse */
        btnAlg.setOnClickListener(new TraverseClickListener());
        
	        
	        /** INITIALIZE BUTTON; connect *
	        btnAlg.setOnClickListener(new View.OnClickListener() {
	            @Override
	            public void onClick(View v) {
	                    try{
	                    	final AlgTask task = new AlgTask(); 
	                    	ProgressDialog dialog = new ProgressDialog(MainActivity.this);
	                    	dialog.setIndeterminate(true);
	                    	dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
	                        dialog.setCancelable(true);
	                        dialog.setMessage("Initializing");
	                        dialog.setTitle("Communicating...");
	                        dialog.setOnCancelListener(new OnCancelListener() {
								@Override
								public void onCancel(DialogInterface dialog) {
									task.cancel(false);
								}
							});
	                        dialog.show();
	                    	
	                    	task.setContext(MainActivity.this);
	                    	task.setDialog(dialog);
	                    	task.setCallback(MainActivity.this);
	                    	
	                    	// parameters
	                    	updateCfgFromUI();
	                    	
	                    	// execute async task
	                    	task.execute(cfg);
	                    }catch(Exception e){
	                    	Log.e(TAG, "Exception", e);
	                    }
	            }
	        });*/
	        
	}
	  
	  public void taskActionsSetEnabled(boolean enabled){
			btnAlg.setEnabled(enabled);
			btnGetPublicIP.setEnabled(enabled);
			btnNATDetect.setEnabled(enabled);	
	  }
	  
	  /**
	   * Starts internal progressbar with corresponding message
	   */
	  private class InternalProgress implements AsyncTaskListener{
		  private boolean cancelable=true;
		  private boolean canceled=false;
		  private boolean running=false;
		  private boolean taskCancelled=false;
		  private OnCancelListener cancelListener=null;
		
		  public void start(){
			  canceled=false;
			  running=true;
			  taskCancelled=false;
			  
			  getActivity().runOnUiThread(new Runnable() {
				@Override
				public void run() {
					taskActionsSetEnabled(false);
					pbar.setIndeterminate(true);
					pbar.invalidate();					
				}
			});
		  }
		  
		  public void stop(){
			  running=false;			  
			  getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						taskActionsSetEnabled(true);
						pbar.setIndeterminate(false);
						pbar.invalidate();					
					}
				});
		  }
		  
		  public boolean isCanceled(){ return canceled; }
		  public boolean isRunning(){ return running; }
		  public void setOnCancelListener(OnCancelListener cancelListener){
			  this.cancelListener = cancelListener;
		  }
		  
		  public synchronized void cancel(){ 
			  LOGGER.debug("Going to cancel some task");
			  if (canceled==false)
				  pmsg.setText(pmsg.getText()+ " - CANCELLED");
			  canceled = true;
			  if (cancelListener!=null){
				  if (!taskCancelled)
					  cancelListener.onCancel(null);
				  taskCancelled=true;
				  // no stop() call - wait for -1 message to stop
			  } else {
				  this.stop();
			  }
		  }
		  
		@Override
		public void onTaskUpdate(DefaultAsyncProgress progress, int state) {
			if (state==0){
				this.start();
			} else 	if (state==2){ // finish
				this.stop();
			} else if (state==-1){
				this.stop();
			} else {
				if (progress!=null)
					pmsg.setText(progress.getMessage());
				ParametersFragment.this.onTaskUpdate(progress, state);
			}
		}

		@Override
		public void setPublicIP(String IP) { }
	  }
	  
	  private class PublicIPistener implements View.OnClickListener {
		  @Override
          public void onClick(View v) {
              try{
              	final GetPublicIPTask task = new GetPublicIPTask();
              	
              	ProgressDialog dialog = new ProgressDialog(getActivity());
              	dialog.setIndeterminate(true);
              	dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                  dialog.setCancelable(true);
                  dialog.setMessage("Initializing");
                  dialog.setTitle("Obtaining public IP");
                  dialog.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							task.cancel(true);
						}
					});
                  
                  dialog.show();
              	
                   
              	task.setContext(getActivity());
              	task.setDialog(dialog);
              	task.setCallback(ParametersFragment.this);
              	
              	// parameters
              	updateCfgFromUI();
              	
              	// execute async task
              	task.execute(cfg);
              }catch(Exception e){
              	Log.e(TAG, "Exception", e);
              }
          }
	  }

	  private class NATClickListener implements View.OnClickListener {
		  @Override
          public void onClick(View v) {
              try{
              	final NATDetectTask task = new NATDetectTask(); 	                    	
              	task.setContext(getActivity());
              	task.setDialog(null);
              	task.setCallback(iproc);
              	task.setGuiLogger((GuiLogger)getActivity());
              	iproc.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							LOGGER.debug("cancelling NAT detect task");
							task.cancel(false);
						}
					});
              	
              	// parameters
              	updateCfgFromUI();
              	
              	// execute async task
              	iproc.start();
              	task.execute(cfg);
              }catch(Exception e){
              	Log.e(TAG, "Exception", e);
              }
          }
	  }
	  
	  private class TraverseClickListener implements View.OnClickListener {
		  @Override
          public void onClick(View v) {
              try{
              	final TraverseTask task = new TraverseTask(); 	                    	
              	task.setContext(getActivity());
              	task.setDialog(null);
              	task.setCallback(iproc);
              	task.setGuiLogger((GuiLogger)getActivity());
              	iproc.setOnCancelListener(new OnCancelListener() {
						@Override
						public void onCancel(DialogInterface dialog) {
							LOGGER.debug("cancelling NAT detect task");
							task.cancel(false);
						}
					});
              	
              	// parameters
              	updateCfgFromUI();
              	
              	// execute async task
              	iproc.start();
              	task.execute(cfg);
              }catch(Exception e){
              	Log.e(TAG, "Exception", e);
              }
          }
	  }
	  
	  public void setApi(IServerService api){
		  LOGGER.debug("Setting api from acctivity");
		  cfg.setApi(api);
	  }
	  
	@Override
	public synchronized void setPublicIP(String IP) {
		if (IP==null) return;
		ePublicIP.setText(IP);
	}

	@Override
	public void onTaskUpdate(DefaultAsyncProgress progress, int state) {
		final MainFragmentActivity a = (MainFragmentActivity) getActivity();
		a.onTaskUpdate(progress, state);
	}

	@Override
	public void onVisibilityChanged(boolean visible) {
		// TODO Auto-generated method stub
		
	}
}
