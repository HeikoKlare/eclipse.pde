/*******************************************************************************
 * Copyright (c) 2000, 2016 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Lars Vogel <Lars.Vogel@vogella.com> - Bug 487988
 *******************************************************************************/
package org.eclipse.pde.internal.ui.search.dependencies;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.text.bundle.ImportPackageObject;
import org.eclipse.pde.internal.ui.IHelpContextIds;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.parts.WizardCheckboxTablePart;
import org.eclipse.pde.internal.ui.wizards.ListUtil.PluginComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public class UnusedImportsDialog extends TrayDialog {
	private final IPluginModelBase model;
	private final Object[] unused;
	private final String warning;
	private final WizardCheckboxTablePart checkboxTablePart;
	private CheckboxTableViewer choiceViewer;

	static class Comparator extends PluginComparator {

		@Override
		public int compare(Viewer viewer, Object e1, Object e2) {
			if (e1.getClass() == e2.getClass()) {
				return super.compare(viewer, e1, e2);
			} else if (e1 instanceof ImportPackageObject) {
				return 1;
			} else {
				return -1;
			}
		}
	}

	class ContentProvider implements IStructuredContentProvider {
		@Override
		public Object[] getElements(Object parent) {
			return unused;
		}
	}

	/**
	 * @param warning a warning to show above the unused dependencies, or
	 *                {@code null} if there is none
	 */
	public UnusedImportsDialog(Shell parentShell, IPluginModelBase model, Object[] unused, String warning) {
		super(parentShell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		this.model = model;
		this.unused = unused;
		this.warning = warning;
		checkboxTablePart = new WizardCheckboxTablePart(PDEUIMessages.UnusedDependencies_remove);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(shell, IHelpContextIds.UNUSED_IMPORTS_DIALOG);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginWidth = layout.marginHeight = 9;
		container.setLayout(layout);
		GridData gd = new GridData(GridData.FILL_BOTH);
		container.setLayoutData(gd);

		if (warning != null) {
			createWarning(container);
		}
		checkboxTablePart.createControl(container);
		choiceViewer = checkboxTablePart.getTableViewer();
		choiceViewer.setContentProvider(new ContentProvider());
		choiceViewer.setLabelProvider(PDEPlugin.getDefault().getLabelProvider());
		choiceViewer.setComparator(new Comparator());

		gd = (GridData) checkboxTablePart.getControl().getLayoutData();
		gd.widthHint = 250;
		gd.heightHint = 275;

		choiceViewer.setInput(PDEPlugin.getDefault());
		checkboxTablePart.setSelection(unused);
		return container;
	}

	private void createWarning(Composite container) {
		Composite warningArea = new Composite(container, SWT.NONE);
		GridLayout layout = new GridLayout(2, false);
		layout.marginWidth = layout.marginHeight = 0;
		warningArea.setLayout(layout);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.horizontalSpan = 2;
		gd.widthHint = convertWidthInCharsToPixels(70);
		warningArea.setLayoutData(gd);
		Label image = new Label(warningArea, SWT.NONE);
		image.setImage(JFaceResources.getImage(Dialog.DLG_IMG_MESSAGE_WARNING));
		image.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		Label text = new Label(warningArea, SWT.WRAP);
		text.setText(warning);
		text.setLayoutData(new GridData(SWT.FILL, SWT.BEGINNING, true, false));
	}

	@Override
	protected void okPressed() {
		GatherUnusedDependenciesOperation.removeDependencies(model, choiceViewer.getCheckedElements());
		super.okPressed();
	}

}
