/**********************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.core.internal.runtime;

import org.eclipse.osgi.util.NLS;

// Runtime plugin message catalog
public class Messages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.core.internal.runtime.messages"; //$NON-NLS-1$

	// Adapter manager
	public static String adapters_badAdapterFactory;

	// application
	public static String application_invalidExtension;
	public static String application_noIdFound;
	public static String application_notFound;
	public static String application_returned;

	// authorization
	public static String auth_notAvailable;

	// line separator platforms
	public static String line_separator_platform_mac_os_9;
	public static String line_separator_platform_unix;
	public static String line_separator_platform_windows;

	// metadata
	public static String meta_appNotInit;
	public static String meta_authFormatChanged;
	public static String meta_exceptionParsingLog;
	public static String meta_keyringFileAlreadySpecified;

	// Extension Registry
	public static String meta_unableToReadAuthorization;
	public static String meta_unableToWriteAuthorization;

	// parsing/resolve
	public static String plugin_deactivatedLoad;

	// plugins
	public static String plugin_shutdownProblems;
	public static String plugin_startupProblems;

	// Preferences
	public static String preferences_saveProblems;

	// Product
	public static String product_notFound;
	public static String provider_invalid;
	public static String provider_invalid_general;

	// Compatibility - parsing/resolve
	public static String parse_badPrereqOnFrag;
	public static String parse_duplicateFragment;
	public static String parse_duplicateLib;
	public static String parse_internalStack;
	public static String parse_unknownElement;
	public static String parse_unknownTopElement;
	public static String parse_unknownAttribute;
	public static String parse_error;
	public static String parse_errorProcessing;
	public static String parse_errorNameLineColumn;
	public static String parse_validExport;
	public static String parse_validMatch;
	public static String parse_unknownLibraryType;
	public static String parse_nullFragmentIdentifier;
	public static String parse_nullPluginIdentifier;
	public static String parse_duplicatePlugin;
	public static String parse_unknownEntry;
	public static String parse_missingPluginId;
	public static String parse_missingPluginName;
	public static String parse_missingFPName;
	public static String parse_missingFPVersion;
	public static String parse_missingPluginVersion;
	public static String parse_fragmentMissingAttr;
	public static String parse_pluginMissingAttr;
	public static String parse_pluginMissingIdName;
	public static String parse_fragmentMissingIdName;
	public static String parse_missingFragmentPd;
	public static String parse_extPointDisabled;
	public static String parse_extPointUnknown;
	public static String parse_unsatisfiedOptPrereq;
	public static String parse_unsatisfiedPrereq;
	public static String parse_prereqDisabled;
	public static String parse_prereqLoop;
	public static String parse_prereqOptLoop;

	// Compatibility - plugins
	public static String plugin_notPluginClass;
	public static String plugin_unableToResolve;
	public static String plugin_pluginDisabled;
	public static String plugin_instantiateClassError;
	public static String plugin_loadClassError;

	static {
		// load message values from bundle file
		reloadMessages();
	}

	public static void reloadMessages() {
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}
}