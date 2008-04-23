/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.provisional.builder;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.pde.api.tools.internal.BundleApiComponent;
import org.eclipse.pde.api.tools.internal.PluginProjectApiComponent;
import org.eclipse.pde.api.tools.internal.builder.ApiAnalysisBuilder;
import org.eclipse.pde.api.tools.internal.builder.BuildState;
import org.eclipse.pde.api.tools.internal.provisional.IApiComponent;
import org.eclipse.pde.api.tools.internal.provisional.IApiProfile;
import org.eclipse.pde.api.tools.internal.provisional.problems.IApiProblem;

/**
 * Description of an analyzer used by the API builder to find and report
 * problems with the current API state.
 * 
 * @see ApiAnalysisBuilder
 * @see IApiProblem
 * @since 1.0.0
 */
public interface IApiAnalyzer {

	/**
	 * Analyzes a given {@link IApiComponent} for API problems.
	 * The component is guaranteed to not be <code>null</code> and to be 
	 * up-to-date in the API description it belongs to.
	 *
	 * @param buildState the given build state or null if none
	 * @param profile the profile context to check the component against
	 * @param component the component to analyze
	 * @param typenames the context of type names to analyze within the given component
	 * @param monitor to report progress
	 * @see PluginProjectApiComponent
	 * @see BundleApiComponent
	 */
	public void analyzeComponent(final BuildState buildState, final IApiProfile profile, final IApiComponent component, final String[] typenames, IProgressMonitor monitor);
	
	/**
	 * Returns the complete set of {@link IApiProblem}s found by this analyzer, or an empty
	 * array. This method must never return <code>null</code>
	 * 
	 * @return the complete set of problems found by this analyzer or an empty array.
	 */
	public IApiProblem[] getProblems();
	
	/**
	 * Cleans up and disposes this analyzer, freeing all held memory.
	 * Once the analyzer has been disposed it cannot be used again without 
	 * specifying a new reporter to use.
	 */
	public void dispose();
	
}
