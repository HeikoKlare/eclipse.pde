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
package org.eclipse.pde.ui.tests.search.dependencies;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.pde.core.plugin.IPluginImport;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.PluginRegistry;
import org.eclipse.pde.core.project.IBundleClasspathEntry;
import org.eclipse.pde.core.project.IBundleProjectDescription;
import org.eclipse.pde.core.project.IBundleProjectService;
import org.eclipse.pde.core.project.IPackageExportDescription;
import org.eclipse.pde.core.project.IPackageImportDescription;
import org.eclipse.pde.core.project.IRequiredBundleDescription;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.ui.search.dependencies.GatherUnusedDependenciesOperation;
import org.eclipse.pde.ui.tests.runtime.TestUtils;
import org.eclipse.pde.ui.tests.util.ProjectUtils;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.osgi.framework.VersionRange;

public class GatherUnusedDependenciesOperationTest {

	@ClassRule
	public static final TestRule CLEAR_WORKSPACE = ProjectUtils.DELETE_ALL_WORKSPACE_PROJECTS_BEFORE_AND_AFTER;

	private static final String EXTENSION_POINT_ID = "clients";

	@Test
	public void testDirectlyUsedDependencyReexportedByOtherDependencyIsNotFlaggedAsUnused() throws Exception {
		// Bundle C: exports a package that is used directly by bundle B
		String bundleC = "bundle.c";
		String packageC = bundleC + ".pkg";
		IProject projectC = createJavaPluginProject(bundleC);
		addExportedPackage(projectC, packageC);
		createJavaSource(projectC, packageC, "C", """
				public class C {
				}
				""");

		// Bundle A: requires and reexports C, but is otherwise unrelated to B
		String bundleA = "bundle.a";
		IProject projectA = createManifestOnlyPluginProject(bundleA);
		addReexportedBundle(projectA, bundleC);

		// Bundle B: directly requires both A and C, and directly uses C's API
		String bundleB = "bundle.b";
		IProject projectB = createJavaPluginProject(bundleB);
		addRequiredBundle(projectB, bundleA);
		addRequiredBundle(projectB, bundleC);
		createJavaSource(projectB, bundleB, "UsesC", """
				public class UsesC {
					%s.C field;
				}
				""".formatted(packageC));

		buildProjects();
		List<String> unusedPlugins = gatherUnusedDependencies(projectB);
		assertFalse(
				"Direct, used dependency to bundle C must not be flagged as unused just because bundle A reexports it",
				unusedPlugins.contains(bundleC));
	}

	@Test
	public void testDependencyOnlyReferencedByJavaTypedAttributeInPluginXmlIsNotFlaggedAsUnused() throws Exception {
		String bundleProvider = "provider.attribute";
		String packageProvider = createProviderBundle(bundleProvider);
		IProject projectConsumer = createConsumerBundle("consumer.attribute", bundleProvider, """
				<client class="%s.Sample"/>
				""".formatted(packageProvider));

		buildProjects();
		List<String> unusedPlugins = gatherUnusedDependencies(projectConsumer);
		assertFalse("Dependency providing a type referenced by a Java-typed attribute must not be flagged as unused",
				unusedPlugins.contains(bundleProvider));
	}

	@Test
	public void testDependencyOnlyReferencedByJavaTypedNestedElementInPluginXmlIsNotFlaggedAsUnused() throws Exception {
		String bundleProvider = "provider.element";
		String packageProvider = createProviderBundle(bundleProvider);
		IProject projectConsumer = createConsumerBundle("consumer.element", bundleProvider, """
				<client>
				         <class class="%s.Sample">
				            <parameter name="key" value="value"/>
				         </class>
				      </client>
				""".formatted(packageProvider));

		buildProjects();
		List<String> unusedPlugins = gatherUnusedDependencies(projectConsumer);
		assertFalse(
				"Dependency providing a type referenced by a Java-typed attribute given as nested element must not be flagged as unused",
				unusedPlugins.contains(bundleProvider));
	}

	@Test
	public void testDependencyNotReferencedByAnyTypeInPluginXmlIsFlaggedAsUnused() throws Exception {
		String bundleProvider = "provider.unreferenced";
		createProviderBundle(bundleProvider);
		IProject projectConsumer = createConsumerBundle("consumer.unreferenced", bundleProvider, """
				<client label="Some plain label"/>
				""");

		buildProjects();
		List<String> unusedPlugins = gatherUnusedDependencies(projectConsumer);
		assertTrue("Dependency whose types are not referenced at all must be flagged as unused",
				unusedPlugins.contains(bundleProvider));
	}

	@Test
	public void testDependencyOnlyReferencedByTypeInElementTextInPluginXmlIsNotFlaggedAsUnused() throws Exception {
		String bundleProvider = "provider.text";
		String packageProvider = createProviderBundle(bundleProvider);
		IProject projectConsumer = createConsumerBundle("consumer.text", bundleProvider, """
				<client>%s.Sample</client>
				""".formatted(packageProvider));

		buildProjects();
		List<String> unusedPlugins = gatherUnusedDependencies(projectConsumer);
		assertFalse("Dependency providing a type referenced as element text must not be flagged as unused",
				unusedPlugins.contains(bundleProvider));
	}

	@Test
	public void testDependencyOfFragmentOnlyReferencedByTypeInFragmentXmlIsNotFlaggedAsUnused() throws Exception {
		String bundleProvider = "provider.fragment";
		String packageProvider = createProviderBundle(bundleProvider);
		String bundleHost = "host.fragment";
		createManifestOnlyPluginProject(bundleHost);
		IProject projectFragment = createConsumerFragment("consumer.fragment", bundleHost, bundleProvider, """
				<client class="%s.Sample"/>
				""".formatted(packageProvider));

		buildProjects();
		List<String> unusedPlugins = gatherUnusedDependencies(projectFragment);
		assertFalse("Dependency providing a type referenced in a fragment.xml must not be flagged as unused",
				unusedPlugins.contains(bundleProvider));
	}

	@Test
	public void testImportedPackageOnlyReferencedByTypeInPluginXmlIsNotFlaggedAsUnused() throws Exception {
		String packageProvider = createProviderBundle("provider.importedused");
		IProject projectConsumer = createImportingConsumerBundle("consumer.importedused", packageProvider, """
				<client class="%s.Sample"/>
				""".formatted(packageProvider));

		buildProjects();
		assertEquals("Imported package providing a referenced type must not be flagged as unused", 0,
				countUnusedImportedPackages(projectConsumer));
	}

	@Test
	public void testImportedPackageNotReferencedByAnyTypeInPluginXmlIsFlaggedAsUnused() throws Exception {
		String packageProvider = createProviderBundle("provider.importedunused");
		IProject projectConsumer = createImportingConsumerBundle("consumer.importedunused", packageProvider, """
				<client label="Some plain label"/>
				""");

		buildProjects();
		assertEquals("Imported package whose types are not referenced at all must be flagged as unused", 1,
				countUnusedImportedPackages(projectConsumer));
	}

	/**
	 * Creates a plug-in project exporting a package that contains a type
	 * {@code Sample} and returns the name of that package.
	 */
	private static String createProviderBundle(String symbolicName) throws Exception {
		String packageName = symbolicName + ".pkg";
		IProject project = createJavaPluginProject(symbolicName);
		addExportedPackage(project, packageName);
		createJavaSource(project, packageName, "Sample", """
				public class Sample {
				}
				""");
		return packageName;
	}

	/**
	 * Creates a plug-in project that requires the given provider bundle and
	 * contributes the given element to an extension point declared by itself.
	 * The project contains no Java source, so the required bundle can only be
	 * referenced from the contributed extension.
	 */
	private static IProject createConsumerBundle(String symbolicName, String providerSymbolicName,
			String extensionContent) throws Exception {
		IProject project = createJavaPluginProject(symbolicName);
		addRequiredBundle(project, providerSymbolicName);
		createExtensionFile(project, "plugin", "plugin.xml", symbolicName, extensionContent);
		return project;
	}

	/**
	 * Creates a plug-in project that imports the given package and contributes
	 * the given element to an extension point declared by itself. The project
	 * contains no Java source, so the imported package can only be referenced
	 * from the contributed extension.
	 */
	private static IProject createImportingConsumerBundle(String symbolicName, String importedPackage,
			String extensionContent) throws Exception {
		IProject project = createJavaPluginProject(symbolicName);
		addImportedPackage(project, importedPackage);
		createExtensionFile(project, "plugin", "plugin.xml", symbolicName, extensionContent);
		return project;
	}

	/**
	 * Creates a fragment project for the given host that requires the given
	 * provider bundle and contributes the given element to an extension point
	 * declared by its host. The project contains no Java source, so the
	 * required bundle can only be referenced from the contributed extension.
	 */
	private static IProject createConsumerFragment(String symbolicName, String hostSymbolicName,
			String providerSymbolicName, String extensionContent) throws Exception {
		IProject project = createJavaPluginProject(symbolicName, hostSymbolicName);
		addRequiredBundle(project, providerSymbolicName);
		createExtensionFile(project, "fragment", "fragment.xml", hostSymbolicName, extensionContent);
		return project;
	}

	private static void createExtensionFile(IProject project, String rootElement, String fileName,
			String declaringSymbolicName, String extensionContent) throws CoreException {
		createFile(project, IPath.fromOSString(fileName), """
				<?xml version="1.0" encoding="UTF-8"?>
				<?eclipse version="3.4"?>
				<%1$s>
				   <extension-point id="%3$s" name="Clients"/>
				   <extension point="%2$s.%3$s">
				      %4$s
				   </extension>
				</%1$s>
				""".formatted(rootElement, declaringSymbolicName, EXTENSION_POINT_ID, extensionContent.strip()));
	}

	private static void createFile(IProject project, IPath path, String content) throws CoreException {
		IContainer container = project;
		for (String segment : path.removeLastSegments(1).segments()) {
			IFolder folder = container.getFolder(IPath.fromOSString(segment));
			if (!folder.exists()) {
				folder.create(true, true, null);
			}
			container = folder;
		}
		IFile file = container.getFile(IPath.fromOSString(path.lastSegment()));
		file.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, null);
	}

	private static IProject createManifestOnlyPluginProject(String symbolicName) throws Exception {
		IBundleProjectService service = acquireBundleProjectService();
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(symbolicName);
		IBundleProjectDescription description = service.getDescription(project);
		description.setSymbolicName(symbolicName);
		description.setNatureIds(new String[] { IBundleProjectDescription.PLUGIN_NATURE });
		description.apply(null);
		return project;
	}

	private static IProject createJavaPluginProject(String symbolicName) throws Exception {
		return createJavaPluginProject(symbolicName, null);
	}

	private static IProject createJavaPluginProject(String symbolicName, String hostSymbolicName) throws Exception {
		IBundleProjectService service = acquireBundleProjectService();
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(symbolicName);
		IBundleProjectDescription description = service.getDescription(project);
		description.setSymbolicName(symbolicName);
		if (hostSymbolicName != null) {
			description.setHost(service.newHost(hostSymbolicName, (VersionRange) null));
		}
		description.setNatureIds(new String[] { IBundleProjectDescription.PLUGIN_NATURE, JavaCore.NATURE_ID });
		IBundleClasspathEntry classpathEntry = service.newBundleClasspathEntry(IPath.fromOSString("src"), null,
				IPath.fromOSString("."));
		description.setBundleClasspath(new IBundleClasspathEntry[] { classpathEntry });
		description.apply(null);
		return project;
	}

	private static void addExportedPackage(IProject project, String packageName) throws Exception {
		IBundleProjectService service = acquireBundleProjectService();
		IBundleProjectDescription description = service.getDescription(project);
		IPackageExportDescription[] presentExports = description.getPackageExports();
		IPackageExportDescription addedExport = service.newPackageExport(packageName, null, true, List.of());
		description.setPackageExports(Stream
				.concat(presentExports != null ? Arrays.stream(presentExports) : Stream.empty(), Stream.of(addedExport))
				.toArray(IPackageExportDescription[]::new));
		description.apply(null);
	}

	private static void addImportedPackage(IProject project, String packageName) throws CoreException {
		IBundleProjectService service = acquireBundleProjectService();
		IBundleProjectDescription description = service.getDescription(project);
		IPackageImportDescription[] presentImports = description.getPackageImports();
		IPackageImportDescription addedImport = service.newPackageImport(packageName, (VersionRange) null, false);
		description.setPackageImports(Stream
				.concat(presentImports != null ? Arrays.stream(presentImports) : Stream.empty(), Stream.of(addedImport))
				.toArray(IPackageImportDescription[]::new));
		description.apply(null);
	}

	private static void addRequiredBundle(IProject project, String symbolicName) throws CoreException {
		addRequiredBundle(project, symbolicName, false);
	}

	private static void addReexportedBundle(IProject project, String symbolicName) throws CoreException {
		addRequiredBundle(project, symbolicName, true);
	}

	private static void addRequiredBundle(IProject project, String symbolicName, boolean reexported)
			throws CoreException {
		IBundleProjectService service = acquireBundleProjectService();
		IBundleProjectDescription description = service.getDescription(project);
		IRequiredBundleDescription[] presentBundles = description.getRequiredBundles();
		IRequiredBundleDescription addedBundle = service.newRequiredBundle(symbolicName, (VersionRange) null, false,
				reexported);
		description.setRequiredBundles(Stream
				.concat(presentBundles != null ? Arrays.stream(presentBundles) : Stream.empty(), Stream.of(addedBundle))
				.toArray(IRequiredBundleDescription[]::new));
		description.apply(null);
	}

	private static void createJavaSource(IProject project, String packageName, String typeName, String body)
			throws CoreException {
		IPath packagePath = IPath.fromOSString("src").append(packageName.replace('.', '/'));
		IFolder packageFolder = project.getFolder(packagePath);
		if (!packageFolder.exists()) {
			IFolder parent = project.getFolder(IPath.fromOSString("src"));
			for (String segment : packageName.split("\\.")) {
				parent = parent.getFolder(segment);
				if (!parent.exists()) {
					parent.create(true, true, null);
				}
			}
		}
		IFile javaFile = packageFolder.getFile(typeName + ".java");
		String content = """
				package %s;

				%s""".formatted(packageName, body);
		javaFile.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, null);
	}

	private static IBundleProjectService acquireBundleProjectService() {
		return PDECore.getDefault().acquireService(IBundleProjectService.class);
	}

	private static void buildProjects() throws CoreException {
		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
		TestUtils.waitForJobs(GatherUnusedDependenciesOperationTest.class.getName(), 100, 10000);
	}

	private static List<String> gatherUnusedDependencies(IProject project)
			throws InvocationTargetException, InterruptedException {
		return gatherUnusedElements(project).stream().filter(IPluginImport.class::isInstance)
				.map(IPluginImport.class::cast).map(IPluginImport::getId).toList();
	}

	/**
	 * Returns the number of imported packages that are flagged as unused, that
	 * is, all flagged elements that are not required bundles.
	 */
	private static long countUnusedImportedPackages(IProject project)
			throws InvocationTargetException, InterruptedException {
		return gatherUnusedElements(project).stream().filter(element -> !(element instanceof IPluginImport)).count();
	}

	private static List<Object> gatherUnusedElements(IProject project)
			throws InvocationTargetException, InterruptedException {
		IPluginModelBase model = PluginRegistry.findModel(project);
		assertNotNull("Plug-in model for bundle " + project.getName() + " not found", model);

		GatherUnusedDependenciesOperation operation = new GatherUnusedDependenciesOperation(model);
		operation.run(new NullProgressMonitor());

		return operation.getList();
	}

}
