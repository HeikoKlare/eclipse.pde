/*******************************************************************************
 *  Copyright (c) 2005, 2015 IBM Corporation and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.search.dependencies;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.swt.widgets.Display;

public class UnusedDependenciesJob extends Job {

	private final IPluginModelBase fModel;
	private final boolean fReadOnly;

	public UnusedDependenciesJob(String name, IPluginModelBase model, boolean readOnly) {
		super(name);
		fModel = model;
		fReadOnly = readOnly;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		try {
			GatherUnusedDependenciesOperation udo = new GatherUnusedDependenciesOperation(fModel);
			udo.run(monitor);
			Set<String> extensionPoints = ExtensionPackageFinder.findExtensionPointsWithoutSchema(fModel);
			String warning = extensionPoints.isEmpty() ? null
					: NLS.bind(PDEUIMessages.UnusedDependencies_missingSchemas, String.join(", ", extensionPoints)); //$NON-NLS-1$
			// List can contain IPluginImports or ImportPackageObjects
			showResults(udo.getList().toArray(), warning);
		} catch (InvocationTargetException | InterruptedException e) {
		} finally {
			monitor.done();
		}
		return Status.OK_STATUS;
	}

	private Action getShowResultsAction(Object[] unused, String warning) {
		return new ShowResultsAction(fModel, unused, fReadOnly, warning);
	}

	protected void showResults(final Object[] unused, String warning) {
		Display.getDefault().asyncExec(() -> getShowResultsAction(unused, warning).run());
	}
}
