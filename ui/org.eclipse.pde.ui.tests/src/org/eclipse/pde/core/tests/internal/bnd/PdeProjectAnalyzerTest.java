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
package org.eclipse.pde.core.tests.internal.bnd;

import static org.eclipse.pde.ui.tests.util.ProjectUtils.createJavaSource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.pde.core.project.IBundleClasspathEntry;
import org.eclipse.pde.core.project.IBundleProjectDescription;
import org.eclipse.pde.core.project.IBundleProjectService;
import org.eclipse.pde.core.project.IRequiredBundleDescription;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.bnd.PdeProjectAnalyzer;
import org.eclipse.pde.ui.tests.runtime.TestUtils;
import org.eclipse.pde.ui.tests.util.ProjectUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.osgi.framework.Constants;
import org.osgi.framework.VersionRange;

import aQute.bnd.osgi.Descriptors.PackageRef;

/**
 * Tests that the analyzer adopts exactly those headers of the analyzed
 * project's manifest that its client asks for. Every header takes effect as an
 * instruction for the analysis, so a client that does not ask for a header must
 * not be subject to its effects. Require-Bundle serves as the example here: it
 * removes the packages that the required bundles provide from the computed
 * imports, because in OSGi those packages are obtained through Require-Bundle
 * instead of Import-Package.
 */
public class PdeProjectAnalyzerTest {

	private static final String REQUIRED_BUNDLE = "org.eclipse.e4.core.contexts";
	private static final String PACKAGE_OF_REQUIRED_BUNDLE = "org.eclipse.e4.core.contexts";

	@ClassRule
	public static final TestRule CLEAR_WORKSPACE = ProjectUtils.DELETE_ALL_WORKSPACE_PROJECTS_BEFORE_AND_AFTER;

	/** Keeps the projects of one test case from interfering with the others. */
	@Rule
	public final TestRule clearCreatedProjects = ProjectUtils.DELETE_CREATED_WORKSPACE_PROJECTS_AFTER;

	@Test
	public void testHeaderThatIsNotToBeCopiedDoesNotInfluenceTheAnalysis() throws Exception {
		IProject project = createProjectUsingTypeOfRequiredBundle("analyzer.without.requirebundle");

		try (PdeProjectAnalyzer analyzer = new PdeProjectAnalyzer(project, true, Set.of())) {
			List<String> importedPackages = importedPackages(analyzer.calcManifest());

			assertNull("Header must not be adopted although it is not to be copied",
					analyzer.getProperty(Constants.REQUIRE_BUNDLE));
			assertTrue("Package of the required bundle must be imported, as Require-Bundle must not take effect: "
					+ importedPackages, importedPackages.contains(PACKAGE_OF_REQUIRED_BUNDLE));
		}
	}

	@Test
	public void testHeaderThatIsToBeCopiedInfluencesTheAnalysis() throws Exception {
		IProject project = createProjectUsingTypeOfRequiredBundle("analyzer.with.requirebundle");

		try (PdeProjectAnalyzer analyzer = new PdeProjectAnalyzer(project, true, Set.of(Constants.REQUIRE_BUNDLE))) {
			List<String> importedPackages = importedPackages(analyzer.calcManifest());

			assertEquals("Header must be adopted as it is to be copied", REQUIRED_BUNDLE,
					analyzer.getProperty(Constants.REQUIRE_BUNDLE));
			// Without this the expectation below would also be met by an
			// analysis that did not see the project's classes at all
			assertTrue("Package of the required bundle must be referred to",
					referredPackages(analyzer).contains(PACKAGE_OF_REQUIRED_BUNDLE));
			assertFalse("Package of the required bundle must not be imported, as Require-Bundle provides it: "
					+ importedPackages, importedPackages.contains(PACKAGE_OF_REQUIRED_BUNDLE));
		}
	}

	@Test
	public void testHeaderSpelledUnexpectedlyIsNotAdoptedEither() throws Exception {
		// Header names are case-insensitive, so a manifest may spell a header
		// differently than the analyzer does. Adopting a header normalizes its
		// name, so dropping it has to normalize it as well.
		IProject project = createProjectUsingTypeOfRequiredBundle("analyzer.unexpected.spelling");
		respellManifestHeader(project, Constants.REQUIRE_BUNDLE, "require-bundle");

		try (PdeProjectAnalyzer analyzer = new PdeProjectAnalyzer(project, true, Set.of())) {
			List<String> importedPackages = importedPackages(analyzer.calcManifest());

			assertNull("Header must not be adopted although it is not to be copied",
					analyzer.getProperty(Constants.REQUIRE_BUNDLE));
			assertTrue("Package of the required bundle must be imported, as Require-Bundle must not take effect: "
					+ importedPackages, importedPackages.contains(PACKAGE_OF_REQUIRED_BUNDLE));
		}
	}

	private static void respellManifestHeader(IProject project, String header, String spelling) throws Exception {
		IFile manifestFile = project.getFile(IPath.fromPortableString(JarFile.MANIFEST_NAME));
		String manifest;
		try (InputStream contents = manifestFile.getContents()) {
			manifest = new String(contents.readAllBytes(), StandardCharsets.UTF_8);
		}
		String respelled = manifest.replace(header + ":", spelling + ":");
		assertNotEquals("Header " + header + " not found in the manifest of project " + project.getName(), manifest,
				respelled);
		manifestFile.setContents(new ByteArrayInputStream(respelled.getBytes(StandardCharsets.UTF_8)), true, false,
				null);
	}

	private static List<String> referredPackages(PdeProjectAnalyzer analyzer) {
		return analyzer.getReferred().keySet().stream().map(PackageRef::getFQN).toList();
	}

	private static List<String> importedPackages(Manifest manifest) throws Exception {
		String header = manifest.getMainAttributes().getValue(Constants.IMPORT_PACKAGE);
		ManifestElement[] elements = ManifestElement.parseHeader(Constants.IMPORT_PACKAGE, header);
		return elements == null ? List.of() : Arrays.stream(elements).map(ManifestElement::getValue).toList();
	}

	private static IProject createProjectUsingTypeOfRequiredBundle(String symbolicName) throws Exception {
		IBundleProjectService service = PDECore.getDefault().acquireService(IBundleProjectService.class);
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(symbolicName);
		IBundleProjectDescription description = service.getDescription(project);
		description.setSymbolicName(symbolicName);
		description.setNatureIds(new String[] { IBundleProjectDescription.PLUGIN_NATURE, JavaCore.NATURE_ID });
		description.setBundleClasspath(new IBundleClasspathEntry[] {
				service.newBundleClasspathEntry(IPath.fromOSString("src"), null, IPath.fromOSString(".")) });
		description.apply(null);

		IBundleProjectDescription createdDescription = service.getDescription(project);
		createdDescription.setRequiredBundles(new IRequiredBundleDescription[] {
				service.newRequiredBundle(REQUIRED_BUNDLE, (VersionRange) null, false, false) });
		createdDescription.apply(null);

		createJavaSource(project, symbolicName, "UsesRequiredBundle", """
				public class UsesRequiredBundle {
					%s.IEclipseContext context;
				}
				""".formatted(PACKAGE_OF_REQUIRED_BUNDLE));

		ResourcesPlugin.getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor());
		TestUtils.waitForJobs(PdeProjectAnalyzerTest.class.getName(), 100, 10000);
		awaitRequiredBundleOnClasspath(project);
		return project;
	}

	/**
	 * The analyzer resolves the required bundles from the project's class path,
	 * which the container of the plug-in dependencies only provides once it has
	 * been initialized, which happens asynchronously after the project has been
	 * created.
	 */
	private static void awaitRequiredBundleOnClasspath(IProject project) throws Exception {
		IJavaProject javaProject = JavaCore.create(project);
		long timeout = System.currentTimeMillis() + 60_000;
		do {
			boolean isOnClasspath = Arrays.stream(javaProject.getResolvedClasspath(true))
					.filter(entry -> entry.getEntryKind() == IClasspathEntry.CPE_LIBRARY).map(IClasspathEntry::getPath)
					.map(IPath::lastSegment).anyMatch(jar -> jar.startsWith(REQUIRED_BUNDLE + "_"));
			if (isOnClasspath) {
				return;
			}
			TestUtils.waitForJobs(PdeProjectAnalyzerTest.class.getName(), 100, 5000);
		} while (System.currentTimeMillis() < timeout);
		fail("Bundle " + REQUIRED_BUNDLE + " did not appear on the class path of project " + project.getName());
	}

}
