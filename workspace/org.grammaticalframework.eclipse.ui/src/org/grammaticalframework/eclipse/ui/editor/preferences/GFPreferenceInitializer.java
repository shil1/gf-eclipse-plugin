package org.grammaticalframework.eclipse.ui.editor.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreAccess;
import org.eclipse.xtext.ui.editor.preferences.IPreferenceStoreInitializer;
import org.grammaticalframework.eclipse.GFPreferences;
import org.grammaticalframework.eclipse.ui.internal.GFActivator;

public class GFPreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = GFActivator.getInstance().getPreferenceStore();
		try {
			// Set default from environment variables
			store.setDefault(GFPreferences.GF_BIN_PATH, System.getenv("HOME") + "/.cabal/bin/gf");
			store.setDefault(GFPreferences.GF_LIB_PATH, System.getenv("GF_LIB_PATH"));
			store.setDefault(GFPreferences.SHOW_DEBUG, true);
		} catch (SecurityException _) {	}

	}


}