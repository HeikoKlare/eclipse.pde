/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.launcher;

import java.io.*;
import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.*;
import org.eclipse.debug.core.model.*;
import org.eclipse.jdt.launching.*;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.build.*;
import org.eclipse.pde.internal.core.*;

public class WorkbenchLaunchConfigurationDelegate extends LaunchConfigurationDelegate 
			implements ILauncherSettings {
	private static final String KEY_BAD_FEATURE_SETUP =
		"WorkbenchLauncherConfigurationDelegate.badFeatureSetup"; //$NON-NLS-1$
	private static final String KEY_NO_STARTUP =
		"WorkbenchLauncherConfigurationDelegate.noStartup"; //$NON-NLS-1$
	private File fConfigDir = null;
	
	/*
	 * @see ILaunchConfigurationDelegate#launch(ILaunchConfiguration, String)
	 */
	public void launch(
		ILaunchConfiguration configuration,
		String mode,
		ILaunch launch,
		IProgressMonitor monitor)
		throws CoreException {
		try {
			fConfigDir = null;
			monitor.beginTask("", 5); //$NON-NLS-1$
			
			String workspace = configuration.getAttribute(LOCATION + "0", LauncherUtils.getDefaultPath().append("runtime-workbench-workspace").toOSString()); //$NON-NLS-1$ //$NON-NLS-2$
			// Clear workspace and prompt, if necessary
			if (!LauncherUtils.clearWorkspace(configuration, workspace, new SubProgressMonitor(monitor, 1))) {
				monitor.setCanceled(true);
				return;
			}

			// clear config area, if necessary
			if (configuration.getAttribute(CONFIG_CLEAR, false))
				LauncherUtils.clearConfigArea(getConfigDir(configuration), new SubProgressMonitor(monitor, 1));
			launch.setAttribute(ILauncherSettings.CONFIG_LOCATION, getConfigDir(configuration).toString());
			
			
			// create launcher
			IVMInstall launcher = LauncherUtils.createLauncher(configuration);
			monitor.worked(1);
			
			// load the arguments on the launcher
			VMRunnerConfiguration runnerConfig = createVMRunner(configuration);
			if (runnerConfig == null) {
				monitor.setCanceled(true);
				return;
			} 
			monitor.worked(1);
						
			LauncherUtils.setDefaultSourceLocator(configuration, launch);
			PDEPlugin.getDefault().getLaunchesListener().manage(launch);
			launcher.getVMRunner(mode).run(runnerConfig, launch, monitor);		
			monitor.worked(1);
		} catch (CoreException e) {
			monitor.setCanceled(true);
			throw e;
		}
	}

	private VMRunnerConfiguration createVMRunner(ILaunchConfiguration configuration)
		throws CoreException {
		String[] classpath = LauncherUtils.constructClasspath(configuration);
		if (classpath == null) {
			String message = PDEPlugin.getResourceString(KEY_NO_STARTUP);
			throw new CoreException(LauncherUtils.createErrorStatus(message));
		}
		
		// Program arguments
		String[] programArgs = getProgramArguments(configuration);
		if (programArgs == null)
			return null;

		// Environment variables
		String[] envp =
			DebugPlugin.getDefault().getLaunchManager().getEnvironment(configuration);

		VMRunnerConfiguration runnerConfig =
			new VMRunnerConfiguration("org.eclipse.core.launcher.Main", classpath); //$NON-NLS-1$
		runnerConfig.setVMArguments(getVMArguments(configuration));
		runnerConfig.setProgramArguments(programArgs);
		runnerConfig.setEnvironment(envp);
		runnerConfig.setVMSpecificAttributesMap(LauncherUtils.getVMSpecificAttributes(configuration));
		return runnerConfig;
	}
	
	private String[] getProgramArguments(ILaunchConfiguration configuration) throws CoreException {
		ArrayList programArgs = new ArrayList();
		
		// If a product is specified, then add it to the program args
		if (configuration.getAttribute(USE_PRODUCT, false)) {
			programArgs.add("-product"); //$NON-NLS-1$
			programArgs.add(configuration.getAttribute(PRODUCT, "")); //$NON-NLS-1$
		} else {
			// specify the application to launch
			programArgs.add("-application"); //$NON-NLS-1$
			programArgs.add(configuration.getAttribute(APPLICATION, LauncherUtils.getDefaultApplicationName()));
		}
		
		// specify the workspace location for the runtime workbench
		String targetWorkspace =
			configuration.getAttribute(LOCATION + "0", LauncherUtils.getDefaultPath().append("runtime-workbench-workspace").toOSString()); //$NON-NLS-1$ //$NON-NLS-2$
		programArgs.add("-data"); //$NON-NLS-1$
		programArgs.add(targetWorkspace);
		
		boolean isOSGI = PDECore.getDefault().getModelManager().isOSGiRuntime();
		if (configuration.getAttribute(USEFEATURES, false)) {
			validateFeatures();
			IPath installPath = PDEPlugin.getWorkspace().getRoot().getLocation();
			programArgs.add("-install"); //$NON-NLS-1$
			programArgs.add("file:" + installPath.removeLastSegments(1).addTrailingSeparator().toString()); //$NON-NLS-1$
			programArgs.add("-update"); //$NON-NLS-1$
		} else {
			TreeMap pluginMap = LauncherUtils.getPluginsToRun(configuration);
			if (pluginMap == null) 
				return null;
				
			String primaryFeatureId = LauncherUtils.getBrandingPluginID(configuration);
			TargetPlatform.createPlatformConfigurationArea(
					pluginMap,
					getConfigDir(configuration),
					primaryFeatureId,
					LauncherUtils.getAutoStartPlugins(configuration));
			programArgs.add("-configuration"); //$NON-NLS-1$
			if (isOSGI)
				programArgs.add("file:" + new Path(getConfigDir(configuration).getPath()).addTrailingSeparator().toString()); //$NON-NLS-1$
			else
				programArgs.add("file:" + new Path(getConfigDir(configuration).getPath()).append("platform.cfg").toString()); //$NON-NLS-1$ //$NON-NLS-2$
			
			if (!isOSGI) {
				if (primaryFeatureId != null) {
					programArgs.add("-feature"); //$NON-NLS-1$
					programArgs.add(primaryFeatureId);					
				}
				IPluginModelBase bootModel = (IPluginModelBase)pluginMap.get("org.eclipse.core.boot"); //$NON-NLS-1$
				String bootPath = LauncherUtils.getBootPath(bootModel);
				if (bootPath != null && !bootPath.endsWith(".jar")) { //$NON-NLS-1$
					programArgs.add("-boot"); //$NON-NLS-1$
					programArgs.add("file:" + bootPath); //$NON-NLS-1$
				}
			}
		}
		
		// add the output folder names
		programArgs.add("-dev"); //$NON-NLS-1$
		if (PDECore.getDefault().getModelManager().isOSGiRuntime())
			programArgs.add(ClasspathHelper.getDevEntriesProperties(getConfigDir(configuration).toString() + "/dev.properties", true)); //$NON-NLS-1$
		else
			programArgs.add(ClasspathHelper.getDevEntries(true));
		
		// necessary for PDE to know how to load plugins when target platform = host platform
		// see PluginPathFinder.getPluginPaths()
		programArgs.add("-pdelaunch"); //$NON-NLS-1$

		// add tracing, if turned on
		if (configuration.getAttribute(TRACING, false)
				&& !TRACING_NONE.equals(configuration.getAttribute(TRACING_CHECKED, (String) null))) {
			programArgs.add("-debug"); //$NON-NLS-1$
			programArgs.add(
				LauncherUtils.getTracingFileArgument(
					configuration,
					getConfigDir(configuration).toString() + Path.SEPARATOR + ".options")); //$NON-NLS-1$
		}

		// add the program args specified by the user
		StringTokenizer tokenizer =
			new StringTokenizer(configuration.getAttribute(PROGARGS, "")); //$NON-NLS-1$
		while (tokenizer.hasMoreTokens()) {
			programArgs.add(tokenizer.nextToken());
		}
		
		if (!programArgs.contains("-nosplash")) { //$NON-NLS-1$
			programArgs.add(0, "-showsplash"); //$NON-NLS-1$
			programArgs.add(1, computeShowsplashArgument());
		}
		return (String[])programArgs.toArray(new String[programArgs.size()]);
	}
	
	private String[] getVMArguments(ILaunchConfiguration configuration) throws CoreException {
		return new ExecutionArguments(configuration.getAttribute(VMARGS,""),"").getVMArgumentsArray(); //$NON-NLS-1$ //$NON-NLS-2$
	}
			
	private void validateFeatures() throws CoreException {
		IPath installPath = PDEPlugin.getWorkspace().getRoot().getLocation();
		String lastSegment = installPath.lastSegment();
		boolean badStructure = lastSegment == null;
		if (!badStructure) {
			IPath featuresPath = installPath.removeLastSegments(1).append("features"); //$NON-NLS-1$
			badStructure = !lastSegment.equalsIgnoreCase("plugins") //$NON-NLS-1$
					|| !featuresPath.toFile().exists();
		}
		if (badStructure) {
			throw new CoreException(LauncherUtils.createErrorStatus(PDEPlugin
					.getResourceString(KEY_BAD_FEATURE_SETUP)));
		}
		// Ensure important files are present
		ensureProductFilesExist(getProductPath());		
	}
	
	private IPath getInstallPath() {
		return PDEPlugin.getWorkspace().getRoot().getLocation();
	}
	
	private IPath getProductPath() {
		return getInstallPath().removeLastSegments(1);
	}

	private String computeShowsplashArgument() {
		IPath eclipseHome = ExternalModelManager.getEclipseHome();
		IPath fullPath = eclipseHome.append("eclipse"); //$NON-NLS-1$
		return fullPath.toOSString() + " -showsplash 600"; //$NON-NLS-1$
	}

	private void ensureProductFilesExist(IPath productArea) {
		File productDir = productArea.toFile();		
		File marker = new File(productDir, ".eclipseproduct"); //$NON-NLS-1$
		IPath eclipsePath = ExternalModelManager.getEclipseHome();
		if (!marker.exists()) 
			copyFile(eclipsePath, ".eclipseproduct", marker); //$NON-NLS-1$
		
		if (PDECore.getDefault().getModelManager().isOSGiRuntime()) {
			File configDir = new File(productDir, "configuration"); //$NON-NLS-1$
			if (!configDir.exists())
				configDir.mkdirs();		
			File ini = new File(configDir, "config.ini");			 //$NON-NLS-1$
			if (!ini.exists())
				copyFile(eclipsePath.append("configuration"), "config.ini", ini); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			File ini = new File(productDir, "install.ini"); //$NON-NLS-1$
			if (!ini.exists()) 
				copyFile(eclipsePath, "install.ini", ini);		 //$NON-NLS-1$
		}
	}

	private void copyFile(IPath eclipsePath, String name, File target) {
		File source = new File(eclipsePath.toFile(), name);
		if (source.exists() == false)
			return;
		FileInputStream is = null;
		FileOutputStream os = null;
		try {
			is = new FileInputStream(source);
			os = new FileOutputStream(target);
			byte[] buf = new byte[1024];
			long currentLen = 0;
			int len = is.read(buf);
			while (len != -1) {
				currentLen += len;
				os.write(buf, 0, len);
				len = is.read(buf);
			}
		} catch (IOException e) {
		} finally {
			try {
				if (is != null)
					is.close();
				if (os != null)
					os.close();
			} catch (IOException e) {
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#getBuildOrder(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String)
	 */
	protected IProject[] getBuildOrder(ILaunchConfiguration configuration,
			String mode) throws CoreException {
		return computeBuildOrder(LauncherUtils.getAffectedProjects(configuration));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#getProjectsForProblemSearch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String)
	 */
	protected IProject[] getProjectsForProblemSearch(
			ILaunchConfiguration configuration, String mode)
			throws CoreException {
		return LauncherUtils.getAffectedProjects(configuration);
	}
	
	private File getConfigDir(ILaunchConfiguration config) {
		if (fConfigDir == null) {
			try {
				if (config.getAttribute(USEFEATURES, false)) {
					String root = getProductPath().toString();
					if (PDECore.getDefault().getModelManager().isOSGiRuntime())
						root += "/configuration"; //$NON-NLS-1$
					fConfigDir = new File(root);
				} else {
					fConfigDir = LauncherUtils.createConfigArea(config.getName());
				}
			} catch (CoreException e) {
				fConfigDir = LauncherUtils.createConfigArea(config.getName());
			}
		}
		if (!fConfigDir.exists())
			fConfigDir.mkdirs();
		return fConfigDir;
	}
}
