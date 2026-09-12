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
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.pde.core.plugin.IFragmentModel;
import org.eclipse.pde.core.plugin.IPluginAttribute;
import org.eclipse.pde.core.plugin.IPluginElement;
import org.eclipse.pde.core.plugin.IPluginExtension;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.IPluginParent;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.ischema.IMetaAttribute;
import org.eclipse.pde.internal.core.ischema.ISchema;
import org.eclipse.pde.internal.core.ischema.ISchemaAttribute;
import org.eclipse.pde.internal.core.ischema.ISchemaElement;
import org.eclipse.pde.internal.core.natures.PluginProject;
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
 * A value references a type if it resolves to one on the project's class path,
 * which is how the extension registry finds it at runtime and how the builder
 * validates such an attribute as well. The resolved type also yields the
 * package to retain, so that neither the notation of the type name nor any
 * naming convention has to be interpreted.
 * <p>
 * Which attributes hold a type is defined by the schema of the extension point.
 * Where it is available, only those attributes are considered, and a type they
 * declare is trusted even if it does not resolve. Otherwise every value is
 * offered for resolution, which retains a dependency for a value that is not
 * meant to be a type but happens to name one, for example an identifier that
 * repeats a class name.
 * <p>
 * The extensions are read from the file, so references that are only present in
 * an unsaved editor are not seen.
 */
public final class ExtensionPackageFinder {

	private static final String CLASS_ATTRIBUTE = "class"; //$NON-NLS-1$

	private final IJavaProject fJavaProject;
	private final ISchema fSchema;

	/**
	 * Creates a finder for the extensions of one extension point, whose schema
	 * and the class path of the analyzed project form the context that every type
	 * reference is resolved against.
	 *
	 * @param javaProject the project to resolve type references on, or
	 *                    {@code null} if it is no Java project
	 * @param schema      the schema of the extension point, or {@code null} if it
	 *                    is unavailable
	 */
	private ExtensionPackageFinder(IJavaProject javaProject, ISchema schema) {
		fJavaProject = javaProject;
		fSchema = schema;
	}

	/**
	 * Returns the packages of all types that the extensions of the given model
	 * reference in its plugin.xml/fragment.xml.
	 */
	public static Set<String> findPackagesInExtensions(IPluginModelBase model) {
		IResource resource = model.getUnderlyingResource();
		if (resource == null) {
			return Set.of();
		}
		IProject project = resource.getProject();
		IPluginModelBase extensionsModel = loadExtensionsModel(model, project);
		if (extensionsModel == null) {
			return Set.of();
		}
		IJavaProject javaProject = PluginProject.isJavaProject(project) ? JavaCore.create(project) : null;
		return Arrays.stream(extensionsModel.getPluginBase().getExtensions())
				.flatMap(extension -> new ExtensionPackageFinder(javaProject, findSchema(extension))
						.findPackages(extension))
				.collect(Collectors.toSet());
	}

	/**
	 * Loads the extensions that the given model declares in its
	 * plugin.xml/fragment.xml into a model of its own.
	 * <p>
	 * The given model cannot be asked for them: a model that belongs to a
	 * project of the workspace deliberately ignores the extensions while reading
	 * that file and obtains them from the extension registry instead, which only
	 * knows the extension points that can be resolved. A model that belongs to
	 * no project, the kind used for the bundles of the target platform, does
	 * read them from the file, which is why such a model is created for the file
	 * of the project here. The distinction is made in PluginBase#processChild.
	 */
	private static IPluginModelBase loadExtensionsModel(IPluginModelBase model, IProject project) {
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

	private static ISchema findSchema(IPluginExtension extension) {
		ISchema schema = PDECore.getDefault().getSchemaRegistry().getSchema(extension.getPoint());
		// a schema that declares no element at all provides no information
		// about the attributes and is thus of no more use than none
		return schema != null && schema.getElementCount() > 0 ? schema : null;
	}

	private Stream<String> findPackages(IPluginParent parent) {
		return Arrays.stream(parent.getChildren()).filter(IPluginElement.class::isInstance)
				.map(IPluginElement.class::cast)
				.flatMap(element -> Stream.concat(findPackagesOfTypes(element, parent), findPackages(element)));
	}

	private Stream<String> findPackagesOfTypes(IPluginElement element, IPluginParent parent) {
		ISchemaElement schemaElement = fSchema != null ? fSchema.findElement(element.getName()) : null;
		if (schemaElement != null) {
			return Arrays.stream(element.getAttributes())
					.filter(attribute -> isTypeAttribute(schemaElement, attribute.getName()))
					.flatMap(attribute -> findPackageOfType(attribute.getValue(), true).stream());
		}
		if (fSchema != null) {
			// a type attribute may alternatively be given as a nested element
			// that carries the type in its class attribute
			boolean isTypeElement = parent instanceof IPluginElement parentElement
					&& isTypeAttribute(fSchema.findElement(parentElement.getName()), element.getName());
			IPluginAttribute attribute = isTypeElement ? element.getAttribute(CLASS_ATTRIBUTE) : null;
			return attribute == null ? Stream.empty() : findPackageOfType(attribute.getValue(), true).stream();
		}
		return Stream
				.concat(Arrays.stream(element.getAttributes()).map(IPluginAttribute::getValue),
						Stream.of(element.getText()))
				.flatMap(value -> findPackageOfType(value, false).stream());
	}

	/**
	 * Returns the package of the type that the given value references, which is
	 * the package of the type it resolves to, or no package if it resolves to
	 * none. A value that the schema declares to be a type is honored even then,
	 * in which case its package is taken from the name.
	 */
	private Optional<String> findPackageOfType(String value, boolean isDeclaredType) {
		if (value == null) {
			return Optional.empty();
		}
		// be careful: the value may have the form typeName:initializationData
		String typeName = value.trim();
		int initializationDataIndex = typeName.indexOf(':');
		if (initializationDataIndex != -1) {
			typeName = typeName.substring(0, initializationDataIndex).trim();
		}
		IType type = findType(typeName);
		String packageName = ""; //$NON-NLS-1$
		if (type != null) {
			packageName = type.getPackageFragment().getElementName();
		} else if (isDeclaredType) {
			packageName = Signature.getQualifier(typeName);
		}
		// a type of the default package has no package to retain, and it could
		// not be provided by a dependency anyway, as that package is not
		// exportable
		return packageName.isEmpty() ? Optional.empty() : Optional.of(packageName);
	}

	private IType findType(String typeName) {
		if (fJavaProject == null) {
			return null;
		}
		try {
			// member types are separated by a dot rather than by a dollar here
			IType type = fJavaProject.findType(typeName.replace('$', '.'));
			return type != null && type.exists() ? type : null;
		} catch (JavaModelException e) {
			PDEPlugin.logException(e);
			return null;
		}
	}

	private static boolean isTypeAttribute(ISchemaElement schemaElement, String attributeName) {
		if (schemaElement == null) {
			return false;
		}
		ISchemaAttribute attribute = schemaElement.getAttribute(attributeName);
		return attribute != null && attribute.getKind() == IMetaAttribute.JAVA;
	}

}
