package com.phoenix.nattester;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.EditText;

import com.actionbarsherlock.app.SherlockFragment;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.phoenix.nattester.MainFragmentActivity.ViewPagerVisibilityListener;

public class LogFragment extends SherlockFragment implements MessageInterface, GuiLogger, ViewPagerVisibilityListener {
	  private static final Logger LOGGER = LoggerFactory.getLogger(LogFragment.class);
	  public final static String TAG = "LogFragment";
	  
	  protected EditText eLog;
	  public static final int LOG_CONTEXT_GROUP = 30;
	  public static final int LOG_CONTEXT_CLEAR = 30;
	  
	  @Override
	  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    // Inflate the layout for this fragment
	    View view = inflater.inflate(R.layout.log_fragment, container, false);
	    
	    return view;
	  }

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
	}

	@Override
	public void onDetach() {
		super.onDetach();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		LOGGER.debug("onCreateContextMenu(): LogFragment; id=" + v.getId());
		if(v.getId() == R.id.txtLog){
			LOGGER.debug("Long press on log window");
			menu.add(LOG_CONTEXT_GROUP, LOG_CONTEXT_CLEAR, Menu.NONE, "Clear");
		} else
			super.onCreateContextMenu(menu, v, menuInfo);
	}
	
	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		if(item.getGroupId() == LOG_CONTEXT_GROUP){
            switch (item.getItemId())
            {
                case LOG_CONTEXT_CLEAR:
                    LOGGER.debug("Clearing log window");
                    this.clearLog();
                    return true;
            }
		}
		
		return super.onContextItemSelected(item);
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
		
		eLog = (EditText) view.findViewById(R.id.txtLog);
		eLog.setKeyListener(null);
		this.registerForContextMenu(eLog);
		eLog.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				LOGGER.debug("Long click listener triggered");
				return false;
			}
		});
	}
	
	/**
	 * Clears log window
	 */
	public synchronized void clearLog(){
		eLog.setText(""); 
		eLog.setSelection(0);
	}
	
	public synchronized void addMessage(String message){
		if (message==null || message.length()<=0) return;
		eLog.append(message);
		eLog.append("\n");
		Editable b = eLog.getText(); 
		eLog.setSelection(b.length());
	}

	@Override
	public void onVisibilityChanged(boolean visible) {
		// TODO Auto-generated method stub
		
	}
}
