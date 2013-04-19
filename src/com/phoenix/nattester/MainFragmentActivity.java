package com.phoenix.nattester;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.pm.ActivityInfo;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.ActionBar.Tab;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.phoenix.nattester.DefaultAsyncProgress.AsyncTaskListener;
import com.phoenix.nattester.service.IServerService;
import com.phoenix.nattester.service.IServerServiceCallback;
import com.phoenix.nattester.service.ReceivedMessage;
import com.phoenix.nattester.service.ServerService;

public class MainFragmentActivity extends SherlockFragmentActivity  implements AsyncTaskListener, MessageInterface, GuiLogger, IServerServiceCallback{
	private static final Logger LOGGER = LoggerFactory.getLogger(MainFragmentActivity.class);
	public final static String TAG = "MainFragmentActivity";
	public final static String THIS_FILE = "MainFragmentActivity";
	private static final Integer TAB_ID_PREFS = 1;
	private static final Integer TAB_ID_LOG = 2;
	Integer initTabId = null;
	
	
	// Progress bar dialog, waiting for service connection
	protected ProgressDialog progressDialog;
	
	// static application config
	final TaskAppConfig cfg = new TaskAppConfig(); 
	
    private ViewPager mViewPager;
    private TabsAdapter mTabsAdapter;
	private ParametersFragment paramFrag;
	private LogFragment logFrag;
	
	// Implement public interface for the service
	private final IServerServiceCallback.Stub binder = new IServerServiceCallback.Stub() {

		@Override
		public void messageSent(int success) throws RemoteException {
			MainFragmentActivity.this.messageSent(success);
		}

		@Override
		public void messageReceived(ReceivedMessage msg) throws RemoteException {
			MainFragmentActivity.this.messageReceived(msg);
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
			MainFragmentActivity.this.messageSent(success);
		}
		
		@Override
		public void messageReceived(ReceivedMessage msg) throws RemoteException {
			MainFragmentActivity.this.messageReceived(msg);
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
				
				LOGGER.debug("Setting callback in activityXX: " + MainFragmentActivity.this.smallCallback);
				api.setCallback(MainFragmentActivity.this.smallCallback);
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
	
	private void initProgress(String message, String title){
		progressDialog=new ProgressDialog(MainFragmentActivity.this);
		progressDialog.setMessage(message);
		progressDialog.setTitle(title);
		progressDialog.setCancelable(true);
		progressDialog.setCanceledOnTouchOutside(false);
		progressDialog.setIndeterminate(true);
		progressDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				LOGGER.debug("Progressbar canceled");
				MainFragmentActivity.this.finish();
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
		// Action bar sherlock - add fragments
		setContentView(R.layout.fragment_host);
		
		// service stuff
		LOGGER.debug("About to start service");
		
		Intent intent = new Intent(ServerService.class.getName());
		startService(intent);
		bindService(intent, serviceConnection, 0);
		
		// init progressbar that waits for service to bind
		this.initProgress("Initializing...", "Starting & connecting to service");
		
		
        final ActionBar ab = getSupportActionBar();
        ab.setDisplayShowHomeEnabled(false);
        ab.setDisplayShowTitleEnabled(false);        
        ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        ab.setDisplayShowCustomEnabled(false);
        
        Tab prefsTab = ab.newTab().setText("Action");
        Tab logTab = ab.newTab().setText("Log");
        mViewPager = (ViewPager) findViewById(R.id.pager);
        
        mTabsAdapter = new TabsAdapter(this, getSupportActionBar(), mViewPager);
        mTabsAdapter.addTab(prefsTab, ParametersFragment.class, TAB_ID_PREFS);
        mTabsAdapter.addTab(logTab, LogFragment.class, TAB_ID_LOG);
        
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
	public IBinder asBinder() {
		LOGGER.debug("asBinder() called");
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
                MainFragmentActivity.this.runOnUiThread(new Runnable() {
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
	
    /**
     * Listener interface for Fragments accommodated in {@link ViewPager}
     * enabling them to know when it becomes visible or invisible inside the
     * ViewPager.
     */	
    public interface ViewPagerVisibilityListener {
        void onVisibilityChanged(boolean visible);
    }
	
	private class TabsAdapter extends FragmentPagerAdapter implements ViewPager.OnPageChangeListener, ActionBar.TabListener {
		private final Context mContext;
		private final ActionBar mActionBar;
		private final ViewPager mViewPager;
		private final List<String> mTabs = new ArrayList<String>();
		private final List<Integer> mTabsId = new ArrayList<Integer>();
		private boolean hasClearedDetails = false;

		private int mCurrentPosition = -1;
		/**
		 * Used during page migration, to remember the next position
		 * {@link #onPageSelected(int)} specified.
		 */
		private int mNextPosition = -1;

		public TabsAdapter(FragmentActivity activity, ActionBar actionBar, ViewPager pager) {
			super(activity.getSupportFragmentManager());
			mContext = activity;
			mActionBar = actionBar;
			mViewPager = pager;
			mViewPager.setAdapter(this);
			mViewPager.setOnPageChangeListener(this);
		}

		public void addTab(ActionBar.Tab tab, Class<?> clss, int tabId) {
			mTabs.add(clss.getName());
			mTabsId.add(tabId);
			mActionBar.addTab(tab.setTabListener(this));
			notifyDataSetChanged();
		}

		public void removeTabAt(int location) {
			mTabs.remove(location);
			mTabsId.remove(location);
			mActionBar.removeTabAt(location);
			notifyDataSetChanged();
		}

		public Integer getIdForPosition(int position) {
			if (position >= 0 && position < mTabsId.size()) {
				return mTabsId.get(position);
			}
			return null;
		}

		public Integer getPositionForId(int id) {
			int fPos = mTabsId.indexOf(id);
			if (fPos >= 0) {
				return fPos;
			}
			return null;
		}

		@Override
		public int getCount() {
			return mTabs.size();
		}

		@Override
		public Fragment getItem(int position) {
			return Fragment.instantiate(mContext, mTabs.get(position),
					new Bundle());
		}

		@Override
		public void onTabSelected(Tab tab, FragmentTransaction ft) {
			clearDetails();
			if (mViewPager.getCurrentItem() != tab.getPosition()) {
				mViewPager.setCurrentItem(tab.getPosition(), true);
			}
		}

		@Override
		public void onPageSelected(int position) {
			mActionBar.setSelectedNavigationItem(position);

			if (mCurrentPosition == position) {
				LOGGER.warn("Previous position and next position became same ("	+ position + ")");
			}

			mNextPosition = position;
		}

		@Override
		public void onTabReselected(Tab tab, FragmentTransaction ft) {
			// Nothing to do
		}

		@Override
		public void onTabUnselected(Tab tab, FragmentTransaction ft) {
			// Nothing to do
		}

		@Override
		public void onPageScrolled(int position, float positionOffset,
				int positionOffsetPixels) {
			// Nothing to do
		}

		/*
		 * public void setCurrentPosition(int position) { mCurrentPosition =
		 * position; }
		 */

		@Override
		public void onPageScrollStateChanged(int state) {
			switch (state) {
			case ViewPager.SCROLL_STATE_IDLE: {
				if (mCurrentPosition >= 0) {
					sendFragmentVisibilityChange(mCurrentPosition, false);
				}
				if (mNextPosition >= 0) {
					sendFragmentVisibilityChange(mNextPosition, true);
				}
				invalidateOptionsMenu();

				mCurrentPosition = mNextPosition;
				break;
			}
			case ViewPager.SCROLL_STATE_DRAGGING:
				clearDetails();
				hasClearedDetails = true;
				break;
			case ViewPager.SCROLL_STATE_SETTLING:
				hasClearedDetails = false;
				break;
			default:
				break;
			}
		}

		private void clearDetails() {
			;
		}
	}



	@Override
	public void addMessage(String message) {
		logFrag.addMessage(message);
	}

	@Override
	public void setPublicIP(String IP) {
		if(paramFrag==null) return;
		paramFrag.setPublicIP(IP);
	}

	public ProgressDialog getProgressDialog() {
		return progressDialog;
	}

	public ParametersFragment getParamFrag() {
		return paramFrag;
	}
	
	private Fragment getFragmentAt(int position) {
        Integer id = mTabsAdapter.getIdForPosition(position);
        if(id != null) {
            if (id == TAB_ID_PREFS) {
                return paramFrag;
            } else if (id == TAB_ID_LOG) {
                return logFrag;
            }
         }
        throw new IllegalStateException("Unknown fragment index: " + position);
    }

    public Fragment getCurrentFragment() {
        if (mViewPager != null) {
            return getFragmentAt(mViewPager.getCurrentItem());
        }
        return null;
    }
    
    private void sendFragmentVisibilityChange(int position, boolean visibility) {
        try {
            final Fragment fragment = getFragmentAt(position);
            if (fragment instanceof ViewPagerVisibilityListener) {
                ((ViewPagerVisibilityListener) fragment).onVisibilityChanged(visibility);
            }
        }catch(IllegalStateException e) {
            Log.e(THIS_FILE, "Fragment not anymore managed");
        }
    }
    
    @Override
    public void onAttachFragment(Fragment fragment) {
        // This method can be called before onCreate(), at which point we cannot
        // rely on ViewPager.
        // In that case, we will setup the "current position" soon after the
        // ViewPager is ready.
        final int currentPosition = mViewPager != null ? mViewPager.getCurrentItem() : -1;
        Integer tabId = null; 
        if(mTabsAdapter != null) {
            tabId = mTabsAdapter.getIdForPosition(currentPosition);
        }
        if (fragment instanceof ParametersFragment) {
            paramFrag = (ParametersFragment) fragment;
            if (initTabId == tabId && tabId != null && tabId == TAB_ID_PREFS) {
            	paramFrag.onVisibilityChanged(true);
                initTabId = null;
            }
        } else if (fragment instanceof LogFragment) {
            logFrag = (LogFragment) fragment;
            if (initTabId == tabId && tabId != null && tabId == TAB_ID_LOG) {
            	logFrag.onVisibilityChanged(true);
                initTabId = null;
            }
        }
    }

	
    @Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	LOGGER.debug("OnCreateContextMenu(): main activity");
		super.onCreateContextMenu(menu, v, menuInfo);
	}

    
}
