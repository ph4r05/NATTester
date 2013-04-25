package com.phoenix.nattester;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PrefsActivity extends PreferenceActivity {
	
	// we have deprecated method here - I am aware of it, but unfortunately
	// PreferenceFragment is in API and later and there is no alternative to 
	// this in compatibility library yet.
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
	   super.onCreate(savedInstanceState);
	   addPreferencesFromResource(R.xml.settings);
	}
}
