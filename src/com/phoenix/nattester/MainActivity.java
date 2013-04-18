package com.phoenix.nattester;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.service.IServerService;
import com.phoenix.nattester.service.IServerServiceCallback;
import com.phoenix.nattester.service.ReceivedMessage;
import com.phoenix.nattester.service.ServerService;

public class MainActivity extends SherlockFragmentActivity implements AsyncTaskListener, MessageInterface, IServerServiceCallback{
	private static final Logger LOGGER = LoggerFactory.getLogger(MainActivity.class);
	public final static String TAG = "MainActivity";
	public static String ACTIVE_TAB = "activeTab";
	
	protected ProgressDialog progressDialog;
	
	protected Button btnGetPublicIP;
	protected Button btnNATDetect;
	protected Button btnLogClear;
	protected Button btnAlg;
	protected CheckBox checkMaster;
	protected EditText ePublicIP;
	protected EditText ePeerIP;
	protected EditText ePeerPort;
	protected EditText eN;
	protected EditText eLog;
	
	// static application config
	final TaskAppConfig cfg = new TaskAppConfig(); 
	
	// Implement public interface for the service
	private final IServerServiceCallback.Stub binder = new IServerServiceCallback.Stub() {

		@Override
		public void messageSent(int success) throws RemoteException {
			MainActivity.this.messageSent(success);
		}

		@Override
		public void messageReceived(ReceivedMessage msg) throws RemoteException {
			MainActivity.this.messageReceived(msg);
		}
			
	};
	
	private IServerServiceCallback smallCallback = new IServerServiceCallback() {
		@Override
		public IBinder asBinder() {
			LOGGER.debug("AS binder? returning binder: " + binder);
			return binder;
		}
		
		@Override
		public void messageSent(int success) throws RemoteException {
			MainActivity.this.messageSent(success);
		}
		
		@Override
		public void messageReceived(ReceivedMessage msg) throws RemoteException {
			MainActivity.this.messageReceived(msg);
		}
	};
	
	private IServerService api;
	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			Log.i(TAG, "Service connection established");
			
			// that's how we get the client side of the IPC connection
			api = IServerService.Stub.asInterface(service);
			try {
				cfg.setApi(api);
				
				LOGGER.debug("Setting callback in activityXX: " + MainActivity.this.smallCallback);
				api.setCallback(MainActivity.this.smallCallback);
				api.startServer();
				
				// progress dialog is not needed now
				progressDialog.setMessage("Done");        		
				progressDialog.dismiss();
			} catch (RemoteException e) {
				LOGGER.error("Failed to add listener", e);
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			Log.i(TAG, "Service connection closed");
			api=null;
		}
	};
	
	
	public final void updateCfgFromUI(){
		try {
			cfg.setMaster(checkMaster.isChecked());
			cfg.setN(Integer.parseInt(eN.getText().toString()));
			cfg.setPeerIP(ePeerIP.getText().toString());
			cfg.setPeerPort(Integer.parseInt(ePeerPort.getText().toString()));
			cfg.setPublicIP(ePublicIP.getText().toString());
			cfg.setStunPort(3478);
			cfg.setStunServer("89.29.122.60");
			cfg.setUpdateCallback(this);
		} catch(Exception e){
			LOGGER.error("Problem during fetching app config", e);
		}
	}
	
	private void initProgress(String message, String title){
		progressDialog=new ProgressDialog(MainActivity.this);
		progressDialog.setMessage(message);
		progressDialog.setTitle(title);
		progressDialog.setCancelable(true);
		progressDialog.setCanceledOnTouchOutside(false);
		progressDialog.setIndeterminate(true);
		progressDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				LOGGER.debug("Progressbar canceled");
				MainActivity.this.finish();
			}
		});
		
		progressDialog.setOnDismissListener(new OnDismissListener() {
			@Override
			public void onDismiss(DialogInterface dialog) {
				LOGGER.debug("Progressbar dismissed");
			}
		});
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// service stuff
		LOGGER.debug("About to start service");
		
		Intent intent = new Intent(ServerService.class.getName());
		startService(intent);
		bindService(intent, serviceConnection, 0);
		
		// init progressbar that waits for service to bind
		this.initProgress("Initializing...", "Starting & connecting to service");
		
		// initialize controls
		btnGetPublicIP = (Button) findViewById(R.id.btnGetPublicIP);
		btnNATDetect = (Button) findViewById(R.id.btnNATDetect);
		btnLogClear = (Button) findViewById(R.id.btnClear);
		btnAlg = (Button) findViewById(R.id.btnAlg);
		checkMaster = (CheckBox) findViewById(R.id.checkMaster);
		ePublicIP = (EditText) findViewById(R.id.txtPublic);
		ePeerIP = (EditText) findViewById(R.id.txtPeerIP);
		ePeerPort = (EditText) findViewById(R.id.txtPeerPort);
		eN = (EditText) findViewById(R.id.txtN);
		eLog = (EditText) findViewById(R.id.txtLog);
		eLog.setFocusable(false);
		
		//eLog.setEnabled(false);
		
		
		final EditText feLog = eLog;
		
		// button callbacks
		/** INITIALIZE BUTTON; clear log **/
		btnLogClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    try{
                    	synchronized(feLog){
                    		feLog.setText("");
                    	}
                    }catch(Exception e){
                    	LOGGER.error("Exception during log clear", e);
                    }
            }
        });
		
		/** INITIALIZE BUTTON; get public IP **/
        btnGetPublicIP.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    try{
                    	final GetPublicIPTask task = new GetPublicIPTask();
                    	
                    	ProgressDialog dialog = new ProgressDialog(MainActivity.this);
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
        });
        
        /** INITIALIZE BUTTON; NAT detect **/
        btnNATDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    try{
                    	final NATDetectTask task = new NATDetectTask(); 
                    	ProgressDialog dialog = new ProgressDialog(MainActivity.this);
                    	dialog.setIndeterminate(true);
                    	dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                        dialog.setCancelable(true);
                        dialog.setMessage("Initializing");
                        dialog.setTitle("NAT detection");
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
        });
        
        /** INITIALIZE BUTTON; connect **/
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
        });
        
        // start progress bar and wait for connection
        this.progressDialog.show();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		try {
			LOGGER.debug("About to stop service");
			api.setCallback(null);
			this.unbindService(serviceConnection);
			this.stopService(new Intent(ServerService.class.getName()));
		} catch(Exception e){
			LOGGER.error("Exception during service stopping", e);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getSupportMenuInflater().inflate(R.menu.activity_main, menu);
		
		// Old SDK function before Sherlock was used
		//getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public synchronized void onTaskUpdate(DefaultAsyncProgress progress, int state) {
		if (progress==null) return;
		
		LOGGER.debug("Updating UI: " + progress.toString());
		addMessage(progress.getLongMessage());
	}

	@Override
	public synchronized void setPublicIP(String IP) {
		if (IP==null) return;
		ePublicIP.setText(IP);
	}

	@Override
	public synchronized void addMessage(String message) {
		if (message==null || message.length()<=0) return;
		eLog.append(message);
		eLog.append("\n");
		Editable b = eLog.getText(); 
		eLog.setSelection(b.length());
	}

	@Override
	public IBinder asBinder() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void messageReceived(ReceivedMessage msg) throws RemoteException {
		StringBuilder sb = new StringBuilder();
		LOGGER.debug("Received message: " + msg);
		
		String msgs = msg.getMessage().toString();
		try {
			msgs = new String(msg.getMessage(), "UTF-8");
		} catch(Exception e){
			LOGGER.warn("Received message cannot be converted to string");
		}
		
		sb.append("New message received on UDP port 23456 at ")
			.append(msg.getMilliReceived())
			.append("; source=").append(msg.getSourceIP())
			.append(":").append(msg.getSourcePort())
			.append("; Msg=").append(msgs);
		
		final String toUpdateMsg = sb.toString().trim();
        Thread t = new Thread() {
            public void run() {
                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                    	addMessage(toUpdateMsg);                          
                    }
                });
            };
        };
        t.start();
	}

	@Override
	public void messageSent(int success) throws RemoteException {
		LOGGER.debug("Message sending status: " + success);
	}

}
