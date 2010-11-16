package org.cygx1.snap;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class SnapPreferences extends PreferenceActivity {
	   public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);

	        addPreferencesFromResource(R.xml.snapprefs);
	    }
}
