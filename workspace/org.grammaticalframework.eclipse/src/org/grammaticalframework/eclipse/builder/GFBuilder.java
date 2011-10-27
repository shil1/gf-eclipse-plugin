package org.grammaticalframework.eclipse.builder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Map;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IPreferencesService;
import org.grammaticalframework.eclipse.GFPreferences;

/**
 * Custom GF builder, yeah!
 * Some refs..
 * 	http://wiki.eclipse.org/FAQ_How_do_I_implement_an_incremental_project_builder%3F
 * 	http://www.eclipse.org/articles/Article-Builders/builders.html
 * 
 * TODO Adding of markers to files
 * TODO Should this class actually be moved to the UI plugin?
 * TODO Support for monitor, when building takes a long time (progress, cancellation)
 * 
 * @author John J. Camilleri
 *
 */
public class GFBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = "org.grammaticalframework.eclipse.ui.build.GFBuilderID"; //$NON-NLS-1$

	public static final String BUILD_FOLDER = ".gfbuild"; //$NON-NLS-1$

	public static final Boolean USE_INDIVIDUAL_FOLDERS = false;
	
	private String gfPath;

	private String defaultGFPath = "/home/john/.cabal/bin/gf"; // TODO hardcoded just for testing!

	private boolean showDebug = false;

	private void log(String msg) {
		if (showDebug)
			System.out.println(msg);
	}

	@Override
	protected IProject[] build(int kind, Map<String, String> args, IProgressMonitor monitor) throws CoreException {
		
		// Get some prefs
		IPreferencesService prefs = Platform.getPreferencesService();
		showDebug = prefs.getBoolean(GFPreferences.QUALIFIER, GFPreferences.SHOW_DEBUG, true, null);
		gfPath = prefs.getString(GFPreferences.QUALIFIER, GFPreferences.GF_BIN_PATH, defaultGFPath, null);
		if (gfPath == null || gfPath.trim().isEmpty()) {
			log("Error during build: GF path not specified.");
			return null;
		}
		
		if (kind == IncrementalProjectBuilder.FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	private void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) {
		log("Incremental build on " + delta);
		try {
			delta.accept(new IResourceDeltaVisitor() {
				public boolean visit(IResourceDelta delta) {
					IResource resource = delta.getResource();
					int kind = delta.getKind(); 
					if (kind == IResourceDelta.ADDED || kind == IResourceDelta.CHANGED) {
						if (shouldBuild(resource)) {
							if (USE_INDIVIDUAL_FOLDERS) {
								cleanFile((IFile) resource);
							}
							if (buildFile((IFile) resource)) {
								log("  + " + delta.getResource().getRawLocation());
							} else {
								log("  > Failed: " + delta.getResource().getRawLocation());
							}
						}
						
					}
					return true; // visit children too
				}
			});
			getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private void fullBuild(IProgressMonitor monitor) throws CoreException {
		log("Full build on " + getProject().getName());
		recursiveDispatcher(getProject().members(), new CallableOnResource() {
			public void call(IResource resource) {
				if (shouldBuild(resource)) {
					if (buildFile((IFile) resource)) {
						log("  + " + resource.getName());
					} else {
						log("  > Failed: " + resource.getName());
					}
				}
			}
		});
		getProject().refreshLocal(IResource.DEPTH_INFINITE, monitor);
	}
	
	@Override
	protected void clean(final IProgressMonitor monitor) throws CoreException {
		IPreferencesService prefs = Platform.getPreferencesService();
		showDebug = prefs.getBoolean(GFPreferences.QUALIFIER, GFPreferences.SHOW_DEBUG, true, null);

		log("Clean " + getProject().getName());
		
		// TODO Delete markers with getProject().deleteMarkers()
		recursiveDispatcher(getProject().members(), new CallableOnResource() {
			public void call(IResource resource) {
				if (resource.getType() == IResource.FILE && resource.getFileExtension().equals("gfh")) {
					try {
						resource.delete(true, monitor);
						log("  - " + resource.getName());
					} catch (CoreException e) {
						log("  > Failed: " + resource.getName());
						e.printStackTrace();
					}
				}
			}
		});
	}
  
	
	/**
	 * For recursively applying a function to an IResource 
	 *
	 */
	interface CallableOnResource {
		public void call(IResource resource);
	}
	private void recursiveDispatcher(IResource[] res, CallableOnResource func) {
		try {
			for (IResource r : res) {
				if (r.getType() == IResource.FOLDER) {
					recursiveDispatcher(((IFolder)r).members(), func);
				} else {
					func.call(r);
				}
			}
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Determine if a resource should be built, based on its properties
	 * @param resource
	 * @return
	 */
	private boolean shouldBuild(IResource resource) {
		return resource.getType() == IResource.FILE && resource.getFileExtension().equals("gf");
	}
	
	private String getBuildDirectory(IFile file) {
		String filename = file.getName();
		if (USE_INDIVIDUAL_FOLDERS) {
			return file.getRawLocation().removeLastSegments(1).toOSString()
					+ java.io.File.separator
					+ BUILD_FOLDER
					+ java.io.File.separator
					+ filename
					+ java.io.File.separator;
		} else {
			return file.getRawLocation().removeLastSegments(1).toOSString()
				+ java.io.File.separator
				+ BUILD_FOLDER
				+ java.io.File.separator;
		}
	}

	/**
	 * For a single .gf file, compile it with GF and run "ss -strip -save" to
	 * capture all the GF headers in the build subfolder.
	 * 
	 * TODO Share a single process for the whole build cycle to save on overheads
	 * @param file
	 */
	private boolean buildFile(IFile file) {
		/* 
		 * We want to compile each source file in .gf with these commands:
		 * i --retain HelloEng.gf
		 * ss -strip -save
		 * 
		 * Shell command: echo "ss -strip -save" | gf -retain HelloEng.gf
		 */
		String filename = file.getName();
		String buildDir = getBuildDirectory(file);
		
		ArrayList<String> command = new ArrayList<String>();
		command.add(gfPath);
		command.add("--retain");
		if (USE_INDIVIDUAL_FOLDERS) {
			command.add(String.format("..%1$s..%1$s%2$s", java.io.File.separator, filename));
		} else {
			command.add(".." + java.io.File.separator + filename);
		}
		
		try {
			// Check the build directory and try to create it
			File buildDirFile = new File(buildDir);
			if (!buildDirFile.exists()) {
				buildDirFile.mkdir();
			}
			
			// Piece together our GF process
			ProcessBuilder b = new ProcessBuilder(command);
			b.directory(buildDirFile);
//			b.redirectErrorStream(true);
			Process process = b.start();
			
			// Feed it our commands, then quit
			BufferedWriter processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
			processInput.write("ss -strip -save");
			processInput.newLine();
			processInput.flush();
			processInput.write("quit");
			processInput.newLine();
			processInput.flush();
			
//			BufferedReader processError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
//			String err_str;
//			while ((err_str = processError.readLine()) != null) {
//				log(err_str);
//			}
			BufferedReader processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String out_str;
			while ((out_str = processOutput.readLine()) != null) {
//				log(out_str);
			}
			
			// Tidy up
			processInput.close();
//			processOutput.close();
			process.waitFor();
			return true;
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return false;		
	}
	
	/**
	 * Clean all the files in the build directory for a given file
	 * 
	 * @param file
	 * @return
	 */
	private void cleanFile(IFile file) {
		log("  Cleaning build directory for " + file.getName());
		
		String buildDir = getBuildDirectory(file);
		// Check the build directory and try to create it
		File buildDirFile = new File(buildDir);
		if (buildDirFile.exists()) {
			File[] files = buildDirFile.listFiles();
			for (File f : files) {
				try {
					f.delete();
					log("  - " + f.getName());
				} catch (Exception _) {
					log("  > Failed: " + f.getName());
				}
			}
		}
	}
	
}
