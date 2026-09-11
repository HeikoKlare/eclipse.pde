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
import static org.junit.Assert.assertNull;

import org.eclipse.pde.internal.ui.search.dependencies.ExtensionPackageFinder;
import org.junit.Test;

public class ExtensionPackageFinderTest {

	@Test
	public void testPackageOfQualifiedTypeName() {
		assertEquals("some.pkg", ExtensionPackageFinder.findPackageOfType("some.pkg.Type"));
	}

	@Test
	public void testPackageOfTypeNameWithSurroundingWhitespace() {
		assertEquals("some.pkg", ExtensionPackageFinder.findPackageOfType("  some.pkg.Type\n"));
	}

	@Test
	public void testPackageOfNestedTypeName() {
		assertEquals("some.pkg", ExtensionPackageFinder.findPackageOfType("some.pkg.Type$Nested"));
	}

	@Test
	public void testPackageOfExecutableExtensionWithInitializationData() {
		assertEquals("some.pkg", ExtensionPackageFinder.findPackageOfType("some.pkg.Type:initialization data"));
	}

	@Test
	public void testPackageOfSingleSegmentPackage() {
		assertEquals("pkg", ExtensionPackageFinder.findPackageOfType("pkg.Type"));
	}

	@Test
	public void testNoPackageOfUnqualifiedTypeName() {
		assertNull(ExtensionPackageFinder.findPackageOfType("Type"));
	}

	@Test
	public void testNoPackageOfTypeNameWithoutPackage() {
		assertNull(ExtensionPackageFinder.findPackageOfType("Outer.Nested"));
	}

	@Test
	public void testNoPackageOfIdentifierWithLowerCaseLastSegment() {
		assertNull(ExtensionPackageFinder.findPackageOfType("some.pkg.identifier"));
	}

	@Test
	public void testNoPackageOfPlainText() {
		assertNull(ExtensionPackageFinder.findPackageOfType("Some plain label."));
	}

	@Test
	public void testNoPackageOfPath() {
		assertNull(ExtensionPackageFinder.findPackageOfType("icons/some/Icon.png"));
	}

	@Test
	public void testNoPackageOfTranslatableValue() {
		assertNull(ExtensionPackageFinder.findPackageOfType("%some.Key"));
	}

	@Test
	public void testNoPackageOfUrl() {
		assertNull(ExtensionPackageFinder.findPackageOfType("https://www.eclipse.org/Some"));
	}

	@Test
	public void testNoPackageOfMissingValue() {
		assertNull(ExtensionPackageFinder.findPackageOfType(null));
	}

}
