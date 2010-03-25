/*******************************************************************************
 * Copyright (c) 2000, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ant.internal.ui.launchConfigurations;

import java.util.regex.Pattern;

import org.eclipse.ant.internal.ui.IAntUIConstants;
import org.eclipse.ant.internal.ui.IAntUIHelpContextIds;
import org.eclipse.ant.launching.IAntLaunchConstants;
import org.eclipse.core.externaltools.internal.IExternalToolConstants;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaJRETab;
import org.eclipse.jdt.internal.debug.ui.jres.JREDescriptor;
import org.eclipse.jdt.internal.debug.ui.launcher.VMArgumentsBlock;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.externaltools.internal.launchConfigurations.ExternalToolsUtil;

public class AntJRETab extends JavaJRETab {

	private static final String MAIN_TYPE_NAME= "org.eclipse.ant.internal.launching.remote.InternalAntRunner"; //$NON-NLS-1$
	
	private VMArgumentsBlock fVMArgumentsBlock=  new VMArgumentsBlock();
	private AntWorkingDirectoryBlock fWorkingDirectoryBlock= new AntWorkingDirectoryBlock();

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IAntUIHelpContextIds.ANT_JRE_TAB);
		Composite comp= (Composite)fJREBlock.getControl();
		((GridData)comp.getLayoutData()).grabExcessVerticalSpace= true;
		((GridData)comp.getLayoutData()).verticalAlignment= SWT.FILL;
		
		fVMArgumentsBlock.createControl(comp);
		((GridData)fVMArgumentsBlock.getControl().getLayoutData()).horizontalSpan= 2;
						
		fWorkingDirectoryBlock.createControl(comp);		
		((GridData)fWorkingDirectoryBlock.getControl().getLayoutData()).horizontalSpan= 2;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.debug.ui.launchConfigurations.JavaJRETab#getDefaultJREDescriptor()
	 */
	protected JREDescriptor getDefaultJREDescriptor() {
		return new JREDescriptor() {
			/* (non-Javadoc)
			 * @see org.eclipse.jdt.internal.debug.ui.jres.JREDescriptor#getDescription()
			 */
			public String getDescription() {
				return AntLaunchConfigurationMessages.AntJRETab_2;
			}
		};
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.debug.ui.launchConfigurations.JavaJRETab#getSpecificJREDescriptor()
	 */
	protected JREDescriptor getSpecificJREDescriptor() {
		return new JREDescriptor() {
			/* (non-Javadoc)
			 * @see org.eclipse.jdt.internal.debug.ui.jres.JREDescriptor#getDescription()
			 */
			public String getDescription() {
				return AntLaunchConfigurationMessages.AntJRETab_3;
			}
		};
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#performApply(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		boolean isDefaultJRE = fJREBlock.isDefaultJRE();
        fWorkingDirectoryBlock.setEnabled(!isDefaultJRE);
		fVMArgumentsBlock.setEnabled(!isDefaultJRE);
		configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_INSTALL_NAME, (String)null);
		configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_INSTALL_TYPE, (String)null);
		if (isDefaultJRE) {
			configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH, (String)null);
			configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, (String)null);
			configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, (String)null);
			configuration.setAttribute(IAntLaunchConstants.ATTR_DEFAULT_VM_INSTALL, false);
		} else {
			super.performApply(configuration);
			IVMInstall vm = fJREBlock.getJRE();
			configuration.setAttribute(IAntLaunchConstants.ATTR_DEFAULT_VM_INSTALL, (vm == null ? false : vm.equals(getDefaultVMInstall(configuration))));
			applySeparateVMAttributes(configuration);
			fVMArgumentsBlock.performApply(configuration);
			fWorkingDirectoryBlock.performApply(configuration);
		}
		setLaunchConfigurationWorkingCopy(configuration);
	}
	
	private void applySeparateVMAttributes(ILaunchConfigurationWorkingCopy configuration) {
		configuration.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, MAIN_TYPE_NAME);
		//only set to use the remote ant process factory if the user
		//has not set to use a logger...bug 84608
        boolean userLogger= false;
        try {
    		String args = configuration.getAttribute(IExternalToolConstants.ATTR_TOOL_ARGUMENTS, (String) null);
    		if (args != null) {
    			Pattern pattern = Pattern.compile("\\$\\{.*_prompt.*\\}"); //$NON-NLS-1$
    			IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
    			String[] arguments = ExternalToolsUtil.parseStringIntoList(args);
    			if (arguments != null) {
                    for (int i = 0; i < arguments.length; i++) {
                        String arg = arguments[i];
                        if (arg.equals("-logger")) { //$NON-NLS-1$
                            userLogger= true;
                            break;
                        } else if (!pattern.matcher(arg).find()) {
    						String resolved = manager.performStringSubstitution(arg, false);
    						if (resolved.equals("-logger")) { //$NON-NLS-1$
                                userLogger= true;
                                break;
                            }
    					}
                    }
                }
    		}
        } catch (CoreException e) {
        }
        if (userLogger) {
            configuration.setAttribute(DebugPlugin.ATTR_PROCESS_FACTORY_ID, (String) null);
        } else {
            configuration.setAttribute(DebugPlugin.ATTR_PROCESS_FACTORY_ID, IAntUIConstants.REMOTE_ANT_PROCESS_FACTORY_ID);
        }
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#initializeFrom(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public void initializeFrom(ILaunchConfiguration configuration) {
		super.initializeFrom(configuration);
		fVMArgumentsBlock.initializeFrom(configuration);
		fWorkingDirectoryBlock.initializeFrom(configuration);
		boolean separateVM = !fJREBlock.isDefaultJRE();
		fWorkingDirectoryBlock.setEnabled(separateVM);
		fVMArgumentsBlock.setEnabled(separateVM);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#isValid(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public boolean isValid(ILaunchConfiguration config) {
		return super.isValid(config) && fWorkingDirectoryBlock.isValid(config);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#setLaunchConfigurationDialog(org.eclipse.debug.ui.ILaunchConfigurationDialog)
	 */
	public void setLaunchConfigurationDialog(ILaunchConfigurationDialog dialog) {
		super.setLaunchConfigurationDialog(dialog);
		fWorkingDirectoryBlock.setLaunchConfigurationDialog(dialog);
		fVMArgumentsBlock.setLaunchConfigurationDialog(dialog);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getErrorMessage()
	 */
	public String getErrorMessage() {
		String m = super.getErrorMessage();
		if (m == null) {
			return fWorkingDirectoryBlock.getErrorMessage();
		}
		return m;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#getMessage()
	 */
	public String getMessage() {
		String m = super.getMessage();
		if (m == null) {
			return fWorkingDirectoryBlock.getMessage();
		}
		return m;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#activated(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void activated(ILaunchConfigurationWorkingCopy workingCopy) {
		setLaunchConfigurationWorkingCopy(workingCopy);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#setDefaults(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void setDefaults(ILaunchConfigurationWorkingCopy config) {
		super.setDefaults(config);
		config.setAttribute(IJavaLaunchConfigurationConstants.ATTR_SOURCE_PATH_PROVIDER, "org.eclipse.ant.ui.AntClasspathProvider"); //$NON-NLS-1$
		IVMInstall defaultVMInstall= getDefaultVMInstall(config);
		if (defaultVMInstall != null) {
			config.setAttribute(IAntLaunchConstants.ATTR_DEFAULT_VM_INSTALL, true);
			//setDefaultVMInstallAttributes(defaultVMInstall, config);
			applySeparateVMAttributes(config);
		}
		
	}

	/**
	 * Returns the default {@link IVMInstall} for the given {@link ILaunchConfiguration}, which resolves
	 * to the {@link IVMInstall} for the backing {@link IJavaProject} as specified by the project
	 * attribute in the configuration. If there is no project attribute the workspace default
	 * {@link IVMInstall} is returned.
	 * 
	 * @param config
	 * @return the default {@link IVMInstall} for the given {@link ILaunchConfiguration}
	 */
	private IVMInstall getDefaultVMInstall(ILaunchConfiguration config) {
		try {
			IJavaProject project = JavaRuntime.getJavaProject(config);
			if(project != null) {
				return JavaRuntime.getVMInstall(project);
			}
			return JavaRuntime.getDefaultVMInstall();
		} catch (CoreException e) {
			//core exception thrown for non-Java project
			return JavaRuntime.getDefaultVMInstall();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#deactivated(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void deactivated(ILaunchConfigurationWorkingCopy workingCopy) {}
}
