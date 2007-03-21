/*******************************************************************************
 * Copyright (c) 2005, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.operations;

import java.io.*;
import java.util.*;

import org.eclipse.compare.patch.WorkspacePatcherUI;
import org.eclipse.core.resources.*;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.client.*;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.client.listeners.DiffListener;
import org.eclipse.team.internal.ccvs.core.connection.CVSCommunicationException;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.Policy;
import org.eclipse.ui.IWorkbenchPart;

public abstract class DiffOperation extends SingleCommandOperation {

	private static final int UNIFIED_FORMAT = 0;
	private static final int CONTEXT_FORMAT = 1;
	private static final int STANDARD_FORMAT = 2;
	
	protected boolean isMultiPatch;
	protected boolean includeFullPathInformation;
	protected PrintStream stream;
	protected IPath patchRoot;
	protected boolean patchHasContents;
	protected boolean patchHasNewFiles;
	
	/* see bug 116427 */
	private Object destination = null;
	
	/* see bug 159894 */
	private class CustomizableEOLPrintStream extends PrintStream{

		private boolean error = false;
		
		private String defaultLineEnding = "\n";  //$NON-NLS-1$
		
		public CustomizableEOLPrintStream(PrintStream openStream) {
			super(openStream);
			if(CVSProviderPlugin.getPlugin().isUsePlatformLineend()){
				defaultLineEnding = System.getProperty("line.separator"); //$NON-NLS-1$
			}
		}
		
		public boolean checkError() {
			return error || super.checkError();
		}

		public void println() {
			try{
				write(defaultLineEnding.getBytes());
			} catch (IOException e){
				error = true;
			}
		}
		
		public void println(boolean x) {
			print(x);
			println();
		}
		
		public void println(char x) {
			print(x);
			println();
		}

		public void println(char[] x) {
			print(x);
			println();
		}

		public void println(double x) {
			print(x);
			println();
		}

		public void println(float x) {
			print(x);
			println();
		}

		public void println(int x) {
			print(x);
			println();
		}

		public void println(long x) {
			print(x);
			println();
		}

		public void println(Object x) {
			print(x);
			println();
		}
		
		public void println(String x) {
			print(x);
			println();
		}
	}
	
	public DiffOperation(IWorkbenchPart part, ResourceMapping[] mappings, LocalOption[] options, boolean isMultiPatch, boolean includeFullPathInformation, IPath patchRoot, Object destination) {
		super(part, mappings, options);
		this.isMultiPatch = isMultiPatch;
		this.includeFullPathInformation=includeFullPathInformation;
		this.patchRoot=patchRoot;
		this.patchHasContents=false;
		this.patchHasNewFiles=false;
		this.destination = destination;
	}
	
	protected boolean shouldRun(){
		if (super.shouldRun() == false){
			return false;
		}
		Job[] jobs = Job.getJobManager().find(destination);
		if(jobs.length != 0){
			MessageDialog question = new MessageDialog(getShell(), 
					CVSUIMessages.DiffOperation_CreatePatchConflictTitle, null, 
					NLS.bind(CVSUIMessages.DiffOperation_CreatePatchConflictMessage, destination.toString()), 
					MessageDialog.QUESTION, 
					new String[]{IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL}, 
					1);
			if(question.open() == 0){
				Job.getJobManager().cancel(destination);
			} else {
				return false;
			}
		}
		return true;
	}
	
	public void execute(IProgressMonitor monitor) throws CVSException, InterruptedException {
		try {
			stream = new CustomizableEOLPrintStream(openStream());
			if (isMultiPatch){
				stream.println(WorkspacePatcherUI.getWorkspacePatchHeader());
			}
			super.execute(monitor);
		} finally {
			if (stream != null) {
				stream.close();
			}
		}
	}
 
	/**
	 * Open and return a stream for the diff output.
	 * @return a stream for the diff output
	 */
	protected abstract PrintStream openStream() throws CVSException;

	protected void execute(CVSTeamProvider provider, IResource[] resources, boolean recurse, IProgressMonitor monitor) throws CVSException, InterruptedException {
		
		//add this project to the total projects encountered
		final HashSet newFiles = new HashSet(); //array of ICVSResource - need HashSet to guard for duplicate entries
		final HashSet existingFiles = new HashSet(); //array of IResource - need HashSet to guard for duplicate entries
		
		monitor.beginTask(null,100);
		final IProgressMonitor subMonitor = Policy.subMonitorFor(monitor,10);
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
			cvsResource.accept(new ICVSResourceVisitor() {
				public void visitFile(ICVSFile file) throws CVSException {
					if (!(file.isIgnored()))  {
						if (!file.isManaged() || file.getSyncInfo().isAdded() ){
							//this is a new file
							if (file.exists())
								newFiles.add(file);
						}else if (file.isModified(subMonitor)){
							existingFiles.add(file.getIResource());
						}
					}
				}
				
				public void visitFolder(ICVSFolder folder) throws CVSException {
					// Even if we are not supposed to recurse we still need to go into
					// the root directory.
					if (!folder.exists() || folder.isIgnored() )  {
						return;
					} 
					
					folder.acceptChildren(this);
					
				}
			}, recurse);
		}
		subMonitor.done();
		
		//Check options 
		//Append our diff output to the server diff output.
		// Our diff output includes new files and new files in new directories.
		int format = STANDARD_FORMAT;
	
		LocalOption[] localoptions = getLocalOptions(recurse);
		for (int i = 0; i < localoptions.length; i++)  {
			LocalOption option = localoptions[i];
			if (option.equals(Diff.UNIFIED_FORMAT) ||
				isMultiPatch)  {
				format = UNIFIED_FORMAT;
			} else if (option.equals(Diff.CONTEXT_FORMAT))  {
				format = CONTEXT_FORMAT;
			} 
		}
		
		boolean haveAddedProjectHeader=false;
		
		if (!existingFiles.isEmpty()){
			if (isMultiPatch && !haveAddedProjectHeader){
				haveAddedProjectHeader=true;
				IProject project=resources[0].getProject();
				stream.println(WorkspacePatcherUI.getWorkspacePatchProjectHeader(project));
			}
			try{
				super.execute(provider, (IResource[]) existingFiles.toArray(new IResource[existingFiles.size()]), recurse, Policy.subMonitorFor(monitor, 90));
			} catch(CVSCommunicationException ex){ // see bug 123430
				CVSUIPlugin.openError(getShell(), null, null, ex, CVSUIPlugin.PERFORM_SYNC_EXEC | CVSUIPlugin.LOG_OTHER_EXCEPTIONS);
			} catch (CVSException ex){
				//ignore
			}
		}
		
		if (!newFiles.isEmpty() && Diff.INCLUDE_NEWFILES.isElementOf(localoptions)){
			//Set new file to flag to let us know that we have added something to the current patch
			patchHasNewFiles=true;
			
			if (isMultiPatch &&!haveAddedProjectHeader){
				haveAddedProjectHeader=true;
				IProject project=resources[0].getProject();
				stream.println(WorkspacePatcherUI.getWorkspacePatchProjectHeader(project));
			}
			
			for (Iterator iter = newFiles.iterator(); iter.hasNext();) {
				ICVSFile cvsFile = (ICVSFile) iter.next();
				addFileToDiff(getNewFileRoot(cvsFile), cvsFile,stream,format);				
			}
		}
		
		monitor.done();
	}

	private ICVSFolder getNewFileRoot(ICVSFile cvsFile) {
		ICVSFolder patchRootFolder = getPatchRootFolder();
		if (patchRootFolder != null)
			return patchRootFolder;
		return CVSWorkspaceRoot.getCVSFolderFor(cvsFile.getIResource().getProject());
	}
	
	protected IStatus executeCommand(Session session, CVSTeamProvider provider, ICVSResource[] resources, boolean recurse, IProgressMonitor monitor) throws CVSException, InterruptedException {
		
		DiffListener diffListener = new DiffListener(stream);
		
		IStatus status = Command.DIFF.execute(session,
							Command.NO_GLOBAL_OPTIONS,
							getLocalOptions(recurse),
							resources,
							diffListener,
							monitor);
		
		//Once any run of the Diff commands reports that it has written something to the stream, the patch 
		//in its entirety is considered non-empty - until then keep trying to set the flag.
		if (!patchHasContents)
			patchHasContents = diffListener.wroteToStream();

		return status;
	}

	protected String getTaskName(CVSTeamProvider provider) {
		return NLS.bind(CVSUIMessages.DiffOperation_0, new String[]{provider.getProject().getName()});
	}

	protected String getTaskName() {
		return CVSUIMessages.DiffOperation_1;
	}
	
	private void addFileToDiff(ICVSFolder patchRoot, ICVSFile file, PrintStream printStream, int format) throws CVSException {
		
		String nullFilePrefix = ""; //$NON-NLS-1$
		String newFilePrefix = ""; //$NON-NLS-1$
		String positionInfo = ""; //$NON-NLS-1$
		String linePrefix = ""; //$NON-NLS-1$
		
		String pathString=""; //$NON-NLS-1$

		
		//get the path string for this file
		pathString= file.getRelativePath(patchRoot);
	
		int lines = 0;
		BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getContents()));
		try {
			while (fileReader.readLine() != null)  {
				lines++;
			}
		} catch (IOException e) {
			throw CVSException.wrapException(file.getIResource(), NLS.bind(CVSMessages.CVSTeamProvider_errorAddingFileToDiff, new String[] { pathString }), e); 
		} finally {
			try {
				fileReader.close();
			} catch (IOException e1) {
				//ignore
			}
		}
			
		// Ignore empty files
		if (lines == 0)
			return;
		
		switch (format) {
		case UNIFIED_FORMAT:
			nullFilePrefix = "--- ";	//$NON-NLS-1$
			newFilePrefix = "+++ "; 	//$NON-NLS-1$
			positionInfo = "@@ -0,0 +1," + lines + " @@" ;	//$NON-NLS-1$ //$NON-NLS-2$
			linePrefix = "+"; //$NON-NLS-1$
			break;
			
		case CONTEXT_FORMAT :
			nullFilePrefix = "*** ";	//$NON-NLS-1$
			newFilePrefix = "--- ";		//$NON-NLS-1$
			positionInfo = "--- 1," + lines + " ----";	//$NON-NLS-1$ //$NON-NLS-2$
			linePrefix = "+ ";	//$NON-NLS-1$
			break;
			
		default :
			positionInfo = "0a1," + lines;	//$NON-NLS-1$
		linePrefix = "> ";	//$NON-NLS-1$
					break;
		}
		
		fileReader = new BufferedReader(new InputStreamReader(file.getContents()));
		try {
				
			printStream.println("Index: " + pathString);		//$NON-NLS-1$
			printStream.println("===================================================================");	//$NON-NLS-1$
			printStream.println("RCS file: " + pathString);	//$NON-NLS-1$
			printStream.println("diff -N " + pathString);	//$NON-NLS-1$
			
			
			if (format != STANDARD_FORMAT)  {
				printStream.println(nullFilePrefix + "/dev/null	1 Jan 1970 00:00:00 -0000");	//$NON-NLS-1$
				// Technically this date should be the local file date but nobody really cares.
				printStream.println(newFilePrefix + pathString + "	1 Jan 1970 00:00:00 -0000");	//$NON-NLS-1$
			}
			
			if (format == CONTEXT_FORMAT)  {
				printStream.println("***************");	//$NON-NLS-1$
				printStream.println("*** 0 ****");		//$NON-NLS-1$
			}
			
			printStream.println(positionInfo);
			
			for (int i = 0; i < lines; i++)  {
				printStream.print(linePrefix);
				printStream.println(fileReader.readLine());
			}
		} catch (IOException e) {
			throw CVSException.wrapException(file.getIResource(), NLS.bind(CVSMessages.CVSTeamProvider_errorAddingFileToDiff, new String[] { pathString }), e); 
		} finally  {
			try {
				fileReader.close();
			} catch (IOException e1) {
			}
		}
	}

	public void setStream(PrintStream stream) {
		this.stream = new CustomizableEOLPrintStream(stream);
	}

	protected void reportEmptyDiff() {
        CVSUIPlugin.openDialog(getShell(), new CVSUIPlugin.IOpenableInShell() {
        	public void open(Shell shell) {
        		MessageDialog.openInformation(
        			shell,
        			CVSUIMessages.GenerateCVSDiff_noDiffsFoundTitle, 
        			CVSUIMessages.GenerateCVSDiff_noDiffsFoundMsg); 
        	}
        }, CVSUIPlugin.PERFORM_SYNC_EXEC);
	 }
	
	protected ICVSFolder getLocalRoot(CVSTeamProvider provider) throws CVSException {
		ICVSFolder root = getPatchRootFolder();
		if (root != null)
			return root;
		return super.getLocalRoot(provider);
	}

	private ICVSFolder getPatchRootFolder() {
		if (!isMultiPatch &&
			!includeFullPathInformation){
			//Check to see if the selected patchRoot has enough segments to consider it a folder/resource
			//if not just get the project

			IResource patchFolder = null;
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			if (patchRoot.segmentCount() > 1){
				patchFolder = root.getFolder(patchRoot);
			} else {
				patchFolder = root.getProject(patchRoot.toString());
			}
		
			ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(patchFolder);
			if (!cvsResource.isFolder()) {
				cvsResource = cvsResource.getParent();
			}
			return (ICVSFolder) cvsResource;
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.RepositoryProviderOperation#consultModelsForMappings()
	 */
	public boolean consultModelsForMappings() {
		return false;
	}
	
	public boolean belongsTo(Object family){
		if(family != null && family.equals(destination))
			return true;
		return super.belongsTo(family);
	}

}
