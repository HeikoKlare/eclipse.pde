/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.pde.core.IModel;
import org.eclipse.pde.core.IModelProviderEvent;
import org.eclipse.pde.core.IModelProviderListener;
import org.eclipse.pde.internal.core.ifeature.IFeatureModel;

/**
 * Manages all feature models in the workspace, and maximum one external feature
 * model for a given id and version. While workspace model(s) exist with the
 * same id and version as an external model, the external model is inactive (not
 * exposed).
 */

public class FeatureModelManager {

	/**
	 * All models in workspace, and those external models that have no
	 * corresponding workspace model with the same id and version
	 */
	private FeatureTable fActiveModels;

	/**
	 * External models masked by workspace models with the same id and version.
	 */
	private FeatureTable fInactiveModels;

	private ExternalModelManager fExternalManager;

	private WorkspaceModelManager fWorkspaceManager;

	private IModelProviderListener fProviderListener;

	/**
	 * List of IFeatureModelListener
	 */
	private ArrayList fListeners;

	public FeatureModelManager() {
		fProviderListener = new IModelProviderListener() {
			public void modelsChanged(IModelProviderEvent e) {
				handleModelsChanged(e);
			}
		};
		fListeners = new ArrayList();
	}

	public void connect(WorkspaceModelManager wm, ExternalModelManager em) {
		fExternalManager = em;
		fWorkspaceManager = wm;
		fExternalManager.addModelProviderListener(fProviderListener);
		fWorkspaceManager.addModelProviderListener(fProviderListener);

	}

	public void shutdown() {
		if (fWorkspaceManager != null)
			fWorkspaceManager.removeModelProviderListener(fProviderListener);
		if (fExternalManager != null)
			fExternalManager.removeModelProviderListener(fProviderListener);
	}

	private synchronized void init() {
		if (fActiveModels != null)
			return;
		fActiveModels = new FeatureTable();
		fInactiveModels = new FeatureTable();

		IFeatureModel[] models = fWorkspaceManager.getFeatureModels();
		for (int i = 0; i < models.length; i++) {
			// add all workspace models, including invalid or duplicate (save
			// id, ver)
			fActiveModels.add(models[i]);
		}

		models = fExternalManager.getAllFeatureModels();
		for (int i = 0; i < models.length; i++) {
			if (!models[i].isValid()) {
				// ignore invalid external models
				continue;
			}
			String id = models[i].getFeature().getId();
			String version = models[i].getFeature().getVersion();
			if (fActiveModels.get(id, version).length <= 0) {
				fActiveModels.add(models[i]);
			} else if (fInactiveModels.get(id, version).length <= 0) {
				fInactiveModels.add(models[i]);
			} else {
				// ignore duplicate external models
			}
		}

	}

	/*
	 * @return all active features
	 */
	public IFeatureModel[] getModels() {
		init();
		IFeatureModel[] allModels = fActiveModels.getAll();
		ArrayList valid = new ArrayList(allModels.length);
		for( int i =0; i< allModels.length; i++){
			if(allModels[i].isValid()){
				valid.add(allModels[i]);
			}
		}
		return (IFeatureModel[])valid.toArray(new IFeatureModel[valid.size()]);
	}

	/**
	 * Finds active model with a given id and version
	 * 
	 * @param id
	 * @param version
	 * @return one IFeature model or null
	 */
	public IFeatureModel findFeatureModel(String id, String version) {
		init();
		IFeatureModel[] models = fActiveModels.get(id, version);
		for (int i = 0; i < models.length; i++) {
			if (models[i].isValid()) {
				return models[i];
			}
		}
		return null;
	}

	/**
	 * Finds active models with a given id
	 * 
	 * @param id
	 * @param version
	 * @return IFeature model[]
	 */
	public IFeatureModel[] findFeatureModels(String id) {
		init();
		IFeatureModel[] models = fActiveModels.get(id);
		ArrayList valid = new ArrayList(models.length);
		for( int i =0; i< models.length; i++){
			if(models[i].isValid()){
				valid.add(models[i]);
			}
		}
		return (IFeatureModel[])valid.toArray(new IFeatureModel[valid.size()]);
	}

	private void handleModelsChanged(IModelProviderEvent e) {
		init();
		IFeatureModelDelta delta = processEvent(e);

		Object[] entries = fListeners.toArray();
		for (int i = 0; i < entries.length; i++) {
			((IFeatureModelListener) entries[i]).modelsChanged(delta);
		}
	}

	/**
	 * @param e
	 * @return
	 */
	private synchronized IFeatureModelDelta processEvent(IModelProviderEvent e) {
		FeatureModelDelta delta = new FeatureModelDelta();
		/*
		 * Set of Idvers for which there might be necessary to move a model
		 * between active models and inactive models
		 */
		Set affectedIdVers = null;
		if ((e.getEventTypes() & IModelProviderEvent.MODELS_REMOVED) != 0) {
			IModel[] removed = e.getRemovedModels();
			for (int i = 0; i < removed.length; i++) {
				if (!(removed[i] instanceof IFeatureModel))
					continue;
				IFeatureModel model = (IFeatureModel) removed[i];
				FeatureTable.Idver idver = fActiveModels.remove(model);
				if (idver != null) {
					// may need to activate another model
					if (affectedIdVers == null)
						affectedIdVers = new HashSet();
					affectedIdVers.add(idver);
					delta.add(model, IFeatureModelDelta.REMOVED);
				} else {
					fInactiveModels.remove(model);
				}
			}
		}
		if ((e.getEventTypes() & IModelProviderEvent.MODELS_ADDED) != 0) {
			IModel[] added = e.getAddedModels();
			for (int i = 0; i < added.length; i++) {
				if (!(added[i] instanceof IFeatureModel))
					continue;
				IFeatureModel model = (IFeatureModel) added[i];
				if (model.getUnderlyingResource() != null) {
					FeatureTable.Idver idver = fActiveModels.add(model);
					delta.add(model, IFeatureModelDelta.ADDED);
					// may need to deactivate another model
					if (affectedIdVers == null)
						affectedIdVers = new HashSet();
					affectedIdVers.add(idver);
				} else {
					if (!model.isValid()) {
						// ignore invalid external models
						continue;
					}
					String id = model.getFeature().getId();
					String version = model.getFeature().getVersion();
					if (fInactiveModels.get(id, version).length > 0) {
						// ignore duplicate external models
						continue;
					}
					IFeatureModel[] activeModels = fActiveModels.get(id,
							version);
					for (int j = 0; j < activeModels.length; j++) {
						if (activeModels[j].getUnderlyingResource() == null) {
							// ignore duplicate external models
							continue;
						}
					}
					FeatureTable.Idver idver = fInactiveModels.add(model);
					// may need to activate this model
					if (affectedIdVers == null)
						affectedIdVers = new HashSet();
					affectedIdVers.add(idver);
				}
			}
		}

		/* 1. Reinsert with a new id and version, if necessary */
		if ((e.getEventTypes() & IModelProviderEvent.MODELS_CHANGED) != 0) {
			IModel[] changed = e.getChangedModels();
			for (int i = 0; i < changed.length; i++) {
				if (!(changed[i] instanceof IFeatureModel))
					continue;
				IFeatureModel model = (IFeatureModel) changed[i];

				String id = model.getFeature().getId();
				String version = model.getFeature().getVersion();

				FeatureTable.Idver oldIdver = fActiveModels.get(model);
				if (oldIdver != null && !oldIdver.equals(id, version)) {
					// version changed
					FeatureTable.Idver idver = fActiveModels.add(model);
					if (affectedIdVers == null)
						affectedIdVers = new HashSet();
					affectedIdVers.add(oldIdver);
					affectedIdVers.add(idver);
				}
				/*
				 * no need to check inactive models, because external features
				 * do not chance or version
				 */

			}
		}
		/* 2. Move features between active and inactive tables if necessary */
		adjustExterrnalVisibility(delta, affectedIdVers);
		/*
		 * 3. Changed models that do result in FeatureModelDelta.ADDED or
		 * FeatureModelDelta.Removed fire FeatureModelDelta.CHANGED
		 */
		if ((e.getEventTypes() & IModelProviderEvent.MODELS_CHANGED) != 0) {
			IModel[] changed = e.getChangedModels();
			for (int i = 0; i < changed.length; i++) {
				if (!(changed[i] instanceof IFeatureModel))
					continue;
				IFeatureModel model = (IFeatureModel) changed[i];
				if (!delta.contains(model, IFeatureModelDelta.ADDED
						| IFeatureModelDelta.REMOVED)) {
					delta.add(model, IFeatureModelDelta.CHANGED);
				}

			}
		}
		return delta;
	}

	/**
	 * @param delta
	 * @param affectedIdVers
	 */
	private void adjustExterrnalVisibility(FeatureModelDelta delta,
			Set affectedIdVers) {
		if (affectedIdVers != null) {
			for (Iterator it = affectedIdVers.iterator(); it.hasNext();) {
				FeatureTable.Idver idver = (FeatureTable.Idver) it.next();
				IFeatureModel[] affectedModels = fActiveModels.get(idver);
				if (affectedModels.length > 1) {
					/*
					 * there must have been at least one workspace and one
					 * external model
					 */
					for (int j = 0; j < affectedModels.length; j++) {
						if (affectedModels[j].getUnderlyingResource() == null) {
							// move external to inactive
							fActiveModels.remove(affectedModels[j]);
							fInactiveModels.add(affectedModels[j]);
							delta.add(affectedModels[j],
									IFeatureModelDelta.REMOVED);
						}
					}
				}

				if (affectedModels.length <= 0) {
					// no workspace model
					IFeatureModel[] models = fInactiveModels.get(idver);
					if (models.length > 0) {
						// external model exists, move it to active
						fInactiveModels.remove(models[0]);
						fActiveModels.add(models[0]);
						delta.add(models[0], IFeatureModelDelta.ADDED);
					}
				}
			}
		}
	}

	public void addFeatureModelListener(IFeatureModelListener listener) {
		if (!fListeners.contains(listener))
			fListeners.add(listener);
	}

	public void removeFeatureModelListener(IFeatureModelListener listener) {
		if (fListeners.contains(listener))
			fListeners.remove(listener);
	}

}
