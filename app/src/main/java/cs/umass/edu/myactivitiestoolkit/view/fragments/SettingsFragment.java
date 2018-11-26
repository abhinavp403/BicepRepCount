package cs.umass.edu.myactivitiestoolkit.view.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.View;
import android.widget.EditText;

import cs.umass.edu.myactivitiestoolkit.R;
import cs.umass.edu.myactivitiestoolkit.constants.Constants;

/**
 * The Settings fragment allows the user to modify all shared applications preferences.
 * You will not be required to make any changes to this class.
 *
 * @author CS390MB
 */
public class SettingsFragment extends PreferenceFragment {
    @SuppressWarnings("unused")
    /** used for debugging purposes */
    private static final String TAG = SettingsFragment.class.getName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference);

    }

}