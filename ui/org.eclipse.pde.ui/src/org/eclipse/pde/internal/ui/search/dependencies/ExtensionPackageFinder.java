/*******************************************************************************
 *  Copyright (c) 2026 Vector Informatik GmbH and others.
 *
 *  This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License 2.0
 *  which accompanies this distribution, and is available at
 *  https://www.eclipse.org/legal/epl-2.0/
 *
 *  SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.pde.internal.ui.search.dependencies;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.pde.core.plugin.IFragmentModel;
import org.eclipse.pde.core.plugin.IPluginAttribute;
import org.eclipse.pde.core.plugin.IPluginElement;
import org.eclipse.pde.core.plugin.IPluginExtension;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.IPluginObject;
import org.eclipse.pde.core.plugin.IPluginParent;
import org.eclipse.pde.internal.core.plugin.ExternalFragmentModel;
import org.eclipse.pde.internal.core.plugin.ExternalPluginModel;
import org.eclipse.pde.internal.core.plugin.ExternalPluginModelBase;
import org.eclipse.pde.internal.core.project.PDEProject;
import org.eclipse.pde.internal.ui.PDEPlugin;

/**
 * Finds the packages of Java types that a bundle references in the extensions
 * of its plugin.xml/fragment.xml. Such types are instantiated reflectively by
 * the extension registry at runtime, so the dependency providing them is
 * required even though no compiled class of the bundle refers to it.
 * <p>
 * Whether an attribute contains a type name is defined by the schema of its
 * extension point, but schemas are only available if the declaring bundle's
 * source is part of the target platform. Every value that looks like a
 * qualified type name is therefore considered a potential type reference.
 * Retaining a dependency that is actually unused is harmless compared to
 * removing one that is required at runtime.
 * <p>
 * The extensions are read from the file, so references that are only present in
 * an unsaved editor are not seen.
 */
public final class ExtensionPackageFinder {

	/**
	 * A qualified Java type name, that is, a dot-separated sequence of
	 * identifiers whose last segment starts with an upper-case letter.
	 */
	private static final Pattern QUALIFIED_TYPE_NAME = Pattern.compile(
			"(\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*\\.)+\\p{Lu}\\p{javaJavaIdentifierPart}*"); //$NON-NLS-1$

	private ExtensionPackageFinder() { // static utility
	}

	/**
	 * Returns the packages of all types that the extensions of the given model
	 * reference in its plugin.xml/fragment.xml.
	 */
	public static Set<String> findPackagesInExtensions(IPluginModelBase model) {
		IPluginModelBase extensionsModel = loadExtensionsModel(model);
		if (extensionsModel == null) {
			return Set.of();
		}
		Set<String> packages = new HashSet<>();
		for (IPluginExtension extension : extensionsModel.getPluginBase().getExtensions()) {
			findPackages(extension, packages);
		}
		return packages;
	}

	/**
	 * Returns the package of the type that the given attribute value or element
	 * text references, or {@code null} if it does not denote a qualified type
	 * name.
	 */
	public static String findPackageOfType(String value) {
		if (value == null) {
			return null;
		}
		// strip the optional initialization data appended to executable
		// extension types and normalize nested type separators
		String type = value.trim();
		int initializationDataIndex = type.indexOf(':');
		if (initializationDataIndex != -1) {
			type = type.substring(0, initializationDataIndex).trim();
		}
		type = type.replace('$', '.');
		if (!QUALIFIED_TYPE_NAME.matcher(type).matches()) {
			return null;
		}
		// drop all trailing segments that start with an upper-case letter, as
		// those denote the (potentially nested) type itself
		String packageName = type;
		while (true) {
			int separatorIndex = packageName.lastIndexOf('.');
			if (separatorIndex <= 0) {
				return null;
			}
			packageName = packageName.substring(0, separatorIndex);
			if (!Character.isUpperCase(packageName.charAt(packageName.lastIndexOf('.') + 1))) {
				return packageName;
			}
		}
	}

	/**
	 * Loads the extensions of the given model from its plugin.xml/fragment.xml.
	 * The model itself cannot be used for that purpose: workspace models
	 * deliberately do not read their extensions from that file but obtain them
	 * from the extension registry, which only knows the extension points that
	 * are resolvable. An external model, which does read the file, is therefore
	 * used to parse it, just like the schema to HTML conversion does.
	 */
	private static IPluginModelBase loadExtensionsModel(IPluginModelBase model) {
		IResource resource = model.getUnderlyingResource();
		if (resource == null) {
			return null;
		}
		IProject project = resource.getProject();
		boolean isFragment = model instanceof IFragmentModel;
		IFile file = isFragment ? PDEProject.getFragmentXml(project) : PDEProject.getPluginXml(project);
		if (!file.exists()) {
			return null;
		}
		ExternalPluginModelBase extensionsModel = isFragment ? new ExternalFragmentModel() : new ExternalPluginModel();
		IPath location = project.getLocation();
		if (location != null) {
			extensionsModel.setInstallLocation(location.toOSString());
		}
		try (InputStream stream = new BufferedInputStream(file.getContents(true))) {
			extensionsModel.load(stream, false);
		} catch (CoreException | IOException e) {
			// without the extensions, dependencies that are only referenced
			// from them are reported as unused, so make the cause visible
			PDEPlugin.logException(e);
			return null;
		}
		return extensionsModel.isLoaded() ? extensionsModel : null;
	}

	private static void findPackages(IPluginParent parent, Set<String> packages) {
		for (IPluginObject child : parent.getChildren()) {
			if (child instanceof IPluginElement element) {
				for (IPluginAttribute attribute : element.getAttributes()) {
					addPackageOfType(attribute.getValue(), packages);
				}
				addPackageOfType(element.getText(), packages);
				findPackages(element, packages);
			}
		}
	}

	private static void addPackageOfType(String value, Set<String> packages) {
		String packageName = findPackageOfType(value);
		if (packageName != null) {
			packages.add(packageName);
		}
	}

}
