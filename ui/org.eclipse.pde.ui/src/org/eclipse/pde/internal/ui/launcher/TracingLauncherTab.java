/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.launcher;

import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.*;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.wizards.ListUtil;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.ui.views.properties.PropertySheetPage;

public class TracingLauncherTab
	extends AbstractLauncherTab
	implements ILauncherSettings {

	private Button fTracingCheck;
	private TableViewer fPluginViewer;
	private IPluginModelBase[] fTraceableModels;
	private Properties fMasterOptions = new Properties();
	private Hashtable fPropertySources = new Hashtable();
	private PropertySheetPage fPropertySheet;
	private Label fPropertyLabel;
	private Image fImage;
	
	public TracingLauncherTab() {
		PDEPlugin.getDefault().getLabelProvider().connect(this);
		fImage = PDEPluginImages.DESC_DOC_SECTION_OBJ.createImage();
	}

	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);
		container.setLayout(new GridLayout());
		Dialog.applyDialogFont(container);
		
		createEnableTracingButton(container);
		Label separator = new Label(container, SWT.SEPARATOR | SWT.HORIZONTAL);
		separator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		createSashSection(container);
		setControl(container);		
		WorkbenchHelp.setHelp(container, IHelpContextIds.LAUNCHER_TRACING);
	}
	
	private void createEnableTracingButton(Composite container) {
		fTracingCheck = new Button(container, SWT.CHECK);
		fTracingCheck.setText(PDEPlugin.getResourceString("TracingLauncherTab.tracing"));
		fTracingCheck.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		fTracingCheck.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				masterCheckChanged(true);
				updateLaunchConfigurationDialog();
			}
		});
	}

	private void createSashSection(Composite container) {
		SashForm sashForm = new SashForm(container, SWT.HORIZONTAL);
		sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));
		createPluginViewer(sashForm);
		createPropertySheetClient(sashForm);
	}
	
	private void createPluginViewer(Composite sashForm) {
		Composite composite = new Composite(sashForm, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.marginWidth = layout.marginHeight = 0;
		composite.setLayout(layout);

		Label label = new Label(composite, SWT.NULL);
		label.setText(PDEPlugin.getResourceString("TracingLauncherTab.plugins"));

		fPluginViewer = new TableViewer(composite, SWT.BORDER);
		fPluginViewer.setContentProvider(new ArrayContentProvider());
		fPluginViewer.setLabelProvider(PDEPlugin.getDefault().getLabelProvider());
		fPluginViewer.setSorter(new ListUtil.PluginSorter());
		fPluginViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent e) {
				pluginSelected(getSelectedModel());
			}
		});
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.widthHint = 125;
		gd.heightHint = 100;
		fPluginViewer.getTable().setLayoutData(gd);
		fPluginViewer.setInput(getTraceableModels());
	}
	
	private void createPropertySheetClient(Composite sashForm) {
		Composite tableChild = new Composite(sashForm, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.marginWidth = layout.marginHeight = 0;
		tableChild.setLayout(layout);

		fPropertyLabel = new Label(tableChild, SWT.NULL);
		fPropertyLabel.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		updatePropertyLabel(null);
		createPropertySheet(tableChild);		
	}

	protected void createPropertySheet(Composite parent) {
		Composite composite = new Composite(parent, SWT.BORDER);
		GridLayout layout = new GridLayout();
		layout.marginWidth = layout.marginHeight = 0;
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		fPropertySheet = new PropertySheetPage();
		fPropertySheet.createControl(composite);
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.heightHint = 100;
		gd.widthHint = 125;
		fPropertySheet.getControl().setLayoutData(gd);
		fPropertySheet.makeContributions(new MenuManager(), new ToolBarManager(), null);
	}

	public void dispose() {
		if (fPropertySheet != null)
			fPropertySheet.dispose();
		fImage.dispose();
		PDEPlugin.getDefault().getLabelProvider().disconnect(this);
		super.dispose();
	}

	private IPluginModelBase[] getTraceableModels() {
		if (fTraceableModels == null) {
			PluginModelManager manager = PDECore.getDefault().getModelManager();
			IPluginModelBase[] models = manager.getPlugins();
			ArrayList result = new ArrayList();
			for (int i = 0; i < models.length; i++) {
				if (TracingOptionsManager.isTraceable(models[i]))
					result.add(models[i]);
			}
			fTraceableModels =
				(IPluginModelBase[]) result.toArray(new IPluginModelBase[result.size()]);
		}
		return fTraceableModels;
	}
	
	private IAdaptable getAdaptable(IPluginModelBase model) {
		if (model == null)
			return null;
		IAdaptable adaptable = (IAdaptable) fPropertySources.get(model);
		if (adaptable == null) {
			String id = model.getPluginBase().getId();
			Hashtable defaults =
				PDECore.getDefault().getTracingOptionsManager().getTemplateTable(id);
			adaptable = new TracingPropertySource(model, fMasterOptions, defaults, this);
			fPropertySources.put(model, adaptable);
		}
		return adaptable;
	}
	
	private void masterCheckChanged(boolean userChange) {
		boolean enabled = fTracingCheck.getSelection();
		fPluginViewer.getTable().setEnabled(enabled);
		fPropertySheet.getControl().setEnabled(enabled);
	}

	public void initializeFrom(ILaunchConfiguration config) {
		fMasterOptions.clear();
		fPropertySources.clear();
		try {
			fTracingCheck.setSelection(config.getAttribute(TRACING, false));
			Map options = config.getAttribute(TRACING_OPTIONS, (Map)null);
			if (options == null)
				options = PDECore.getDefault().getTracingOptionsManager().getTracingTemplateCopy();
			else
				options = PDECore.getDefault().getTracingOptionsManager().getTracingOptions(options);
			fMasterOptions.putAll(options);
			masterCheckChanged(false);
			
			IPluginModelBase model = getLastSelectedPlugin(config);
			if (model != null) {
				fPluginViewer.setSelection(new StructuredSelection(model));
			} else {
				pluginSelected(null);
			}
		} catch (CoreException e) {
			PDEPlugin.logException(e);
		}
	}
	
	private IPluginModelBase getLastSelectedPlugin(ILaunchConfiguration config) throws CoreException {
		String pluginID = config.getAttribute(TRACING_SELECTED_PLUGIN, (String)null);
		if (pluginID != null) {
			for (int i = 0; i < fTraceableModels.length; i++) {
				if (fTraceableModels[i].getPluginBase().getId().equals(pluginID))
					return fTraceableModels[i];
			}
		}
		return null;
	}

	public void performApply(ILaunchConfigurationWorkingCopy config) {
		boolean tracingEnabled = fTracingCheck.getSelection();
		config.setAttribute(TRACING, tracingEnabled);
		
		if (tracingEnabled) {
			IPluginModelBase model = getSelectedModel();
			String id = (model == null) ? null : model.getPluginBase().getId();
			config.setAttribute(TRACING_SELECTED_PLUGIN, id);
			
			boolean changes = false;
			for (Enumeration enum = fPropertySources.elements();
				enum.hasMoreElements();
				) {
				TracingPropertySource source = (TracingPropertySource) enum.nextElement();
				if (source.isModified()) {
					changes = true;
					source.save();
				}
			}
			if (changes)
				config.setAttribute(TRACING_OPTIONS, fMasterOptions);
		} else {
			config.setAttribute(TRACING_SELECTED_PLUGIN, (String)null);
		}
	}

	public void setDefaults(ILaunchConfigurationWorkingCopy config) {
		config.setAttribute(TRACING, false);
	}

	private void updatePropertyLabel(IPluginModelBase model) {
		String text =
			(model == null)
				? PDEPlugin.getResourceString("TracingLauncherTab.options")
				: PDEPlugin.getDefault().getLabelProvider().getText(model);
		fPropertyLabel.setText(text);
	}

	private void pluginSelected(IPluginModelBase model) {
		IAdaptable adaptable = getAdaptable(model);
		ISelection selection =
			adaptable != null
				? new StructuredSelection(adaptable)
				: new StructuredSelection();
		fPropertySheet.selectionChanged(null, selection);
		updatePropertyLabel(model);
	}
	
	public String getName() {
		return PDEPlugin.getResourceString("TracingLauncherTab.name");
	}
	
	public Image getImage() {
		return fImage;
	}
	
	private IPluginModelBase getSelectedModel() {
		if (fTracingCheck.isEnabled()) {
			Object item = ((IStructuredSelection) fPluginViewer.getSelection()).getFirstElement();
			if (item instanceof IPluginModelBase)
				return((IPluginModelBase) item);
		} 
		return null;
	}

}
