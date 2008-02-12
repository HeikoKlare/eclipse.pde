package org.eclipse.pde.api.tools.builder.tests;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import junit.framework.AssertionFailedError;
import junit.framework.ComparisonFailure;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.core.Flags;

public class TestCase extends junit.framework.TestCase {

	// Filters
	public static final String METHOD_PREFIX = "test";
	public  static String RUN_ONLY_ID = "ONLY_";

	// Ordering
	public static final int NO_ORDER = 0;
	public static final int ALPHABETICAL_SORT = 1;
	public static final int ALPHA_REVERSE_SORT = 2;
	public static final int RANDOM_ORDER_JDT = 3;
	public static final int RANDOM_ORDER_TIME = 4;

	/**
	 * Expected tests order while building tests list for test suites.
	 * 	@see #buildTestsList(Class, int, long)
	 * <br>
	 * User may use following different values:
	 * 	<ul>
	 *			<li>{@link #NO_ORDER}: none (this is the default)</li>
	 *			<li>{@link #ALPHABETICAL_SORT}: alphabetical order (ie. ascending)</li>
	 *			<li>{@link #ALPHA_REVERSE_SORT}: alpha reverse order (ie. descending )</li>
	 *			<li>{@link #RANDOM_ORDER_JDT}: random order using JDT/Core current version as seed</li>
	 *			<li>{@link #RANDOM_ORDER_TIME}: random order using current time as seed (used time value is displayed in console)</li>
	 *			<li>other values: random order using given <code>long</code> value as seed</li>
	 * 	</ul>
	 * This value is initialized with <code>"ordering"</code> system property.
	 */
	public static final long ORDERING;
	static {
		long ordering = NO_ORDER; // default is no order
		try {
			long seed = Long.parseLong(System.getProperty("ordering", "0"));
			try {
				int kind = Integer.parseInt(System.getProperty("ordering", "0"));
				switch (kind) {
					case NO_ORDER:
						break;
					case ALPHABETICAL_SORT:
						ordering = kind;
						System.err.println("Note that tests will be run sorted using alphabetical order...");
						break;
					case ALPHA_REVERSE_SORT:
						ordering = kind;
						System.err.println("Note that tests will be run sorted using alphabetical reverse order...");
						break;
					case RANDOM_ORDER_TIME:
						ordering = System.currentTimeMillis();
						System.err.println("Note that tests will be run in random order using seed="+ordering+" (ie. current time)");
						break;
					default:
						ordering = seed;
						System.err.println("Note that tests will be run in random order using seed="+seed+" (ie. given value)");
						break;
				}
			} catch (NumberFormatException nfe) {
				// ordering value is over int range but is a valid long => keep the value
				ordering = seed;
				System.err.println("Note that tests will be run in random order using seed="+seed+" (ie. given value)");
			}
		}
		catch (NumberFormatException nfe) {
			System.err.println("Only integer or long values are allowed for 'ordering' system property: "+System.getProperty("ordering", "0")+" is not valid ! => no order will be finally used...");
			ordering = NO_ORDER;
		}
		ORDERING = ordering;
	}
	/**
	 * Flag telling whether test execution must stop on failure or not.
	 * Default is true;
	 */
	protected boolean abortOnFailure = true;

	// static variables for subsets tests
	public static String TESTS_PREFIX = null; // prefix of test names to perform
	public static String[] TESTS_NAMES = null; // list of test names to perform
	public static int[] TESTS_NUMBERS = null; // list of test numbers to perform
	public static int[] TESTS_RANGE = null; // range of test numbers to perform

	public TestCase(String name) {
		setName(name);
	}

public static void assertEquals(String expected, String actual) {
    assertEquals(null, expected, actual);
}
public static void assertEquals(String message, String expected, String actual) {
	assertStringEquals(message, expected, actual, true);
}
public static void assertStringEquals(String expected, String actual, boolean showLineSeparators) {
	assertStringEquals(null, expected, actual, showLineSeparators);
}
public static void assertStringEquals(String message, String expected, String actual, boolean showLineSeparators) {
	if (expected == null && actual == null)
		return;
	if (expected != null && expected.equals(actual))
		return;
	final StringBuffer formatted;
	if (message != null) {
		formatted = new StringBuffer(message).append('.');
	} else {
		formatted = new StringBuffer();
	}
	if (showLineSeparators) {
		final String expectedWithLineSeparators = showLineSeparators(expected);
		final String actualWithLineSeparators = showLineSeparators(actual);
		formatted.append("\n----------- Expected ------------\n"); //$NON-NLS-1$
		formatted.append(expectedWithLineSeparators);
		formatted.append("\n------------ but was ------------\n"); //$NON-NLS-1$
		formatted.append(actualWithLineSeparators);
		formatted.append("\n--------- Difference is ----------\n"); //$NON-NLS-1$
		throw new ComparisonFailure(formatted.toString(),
			    expectedWithLineSeparators, 
			    actualWithLineSeparators);
	} else {
		formatted.append("\n----------- Expected ------------\n"); //$NON-NLS-1$
		formatted.append(expected);
		formatted.append("\n------------ but was ------------\n"); //$NON-NLS-1$
		formatted.append(actual);
		formatted.append("\n--------- Difference is ----------\n"); //$NON-NLS-1$
		throw new ComparisonFailure(formatted.toString(),  expected, actual);
	}
}
/**
 * Same method as {@link #assertEquals(String, int, int)} if the flag
 * {@link #abortOnFailure} has been set to <code>true</code>.
 * Otherwise, the thrown exception {@link AssertionFailedError} is caught
 * and its message is only displayed in the console hence producing no JUnit failure.
 */
protected void assumeEquals(String msg, int expected, int actual) {
	try {
		assertEquals(msg, expected, actual);
	} catch (AssertionFailedError afe) {
		if (abortOnFailure) {
			throw afe;
		}
		printAssertionFailure(afe);
	}
}

/**
 * Same method as {@link #assertEquals(String, long, long)} if the flag
 * {@link #abortOnFailure} has been set to <code>true</code>.
 * Otherwise, the thrown exception {@link AssertionFailedError} is caught
 * and its message is only displayed in the console hence producing no JUnit failure.
 */
protected void assumeEquals(String msg, long expected, long actual) {
	try {
		assertEquals(msg, expected, actual);
	} catch (AssertionFailedError afe) {
		if (abortOnFailure) {
			throw afe;
		}
		printAssertionFailure(afe);
	}
}

/**
 * Same method as {@link #assertTrue(String, boolean)} if the flag
 * {@link #abortOnFailure} has been set to <code>true</code>.
 * Otherwise, the thrown exception {@link AssertionFailedError} is caught
 * and its message is only displayed in the console hence producing no JUnit failure.
 */
protected void assumeTrue(String msg, boolean cond) {
	try {
		assertTrue(msg, cond);
	} catch (AssertionFailedError afe) {
		if (abortOnFailure) {
			throw afe;
		}
		printAssertionFailure(afe);
	}
}

private void printAssertionFailure(AssertionFailedError afe) {
	System.out.println("\n!---!!---!!---!!---!!---!!---!!---!!---!!---!!---!!---!!---!!---!!---!!---!!---!");
	System.out.println("Caught assertion failure while running test "+getName()+":");
	System.out.println("	"+afe.getMessage());
	System.out.println("--------------------------------------------------------------------------------\n");
}

/**
 * Build a list of methods to run for a test suite.
 * There's no recursion in given class hierarchy, methods are only
 * public method starting with "test" of it.
 * <p></p>
 *  Note that this list may be reduced using 2 different mechanism:
 * <p></p>
 * 1) TESTS* static variables:
 * <ul>
 * <li>{@link #TESTS_PREFIX}: only methods starting with this prefix (after "test" of course)
 * 		will be put in test suite.
 * </li>
 * <li>{@link #TESTS_NAMES}: only methods with these names will be put in test suite.
 * </li>
 * <li>{@link #TESTS_NUMBERS}: only methods including these numbers will be put in test suite.<br>
 * 	For example, <code>TESTS_NUMBERS = new int[] { 10, 100, 125678 };</code> will put
 * 	<code>test010()</code>, <code>test100()</code> and <code>testBug125678()</code>
 * 	methods in test suite.
 * </li>
 * <li>{@link #TESTS_RANGE}: only methods which numbers are between first and second value
 * 	of this int array will be put in the suite.
 * 	For example: <code>TESTS_RANGE = new int[] { 10, 12 };</code> will put
 * 	<code>test010()</code>, <code>test011()</code> and <code>test012()</code>
 * 	methods in test suite.<br>
 * 	Note that -1 will clean min or max value, for example <code>TESTS_RANGE = new int[] { 10, -1 };</code>
 * 	will put all methods after <code>test010()</code> in the test suite.
 * </li>
 * </ul>
 * <p></p>
 * 2) testONLY_ methods<br>
 * As static variables needs a static initializer usually put at the beginning of the test suite,
 * it could be a little be boring while adding tests at the end of the file to modify this static initializer.
 * One solution to avoid this was to introduced specific methods name which will be only executed
 * when test suite is run alone.
 * For example:
 * <pre>
 * 	public class MyTest extends TestCase {
 * 		public MyTest(String name) {
 * 			super(name);
 * 		}
 * 		public test001() {
 * 			...
 * 		}
 * 		public test002() {
 * 			...
 * 		}
 * 		...
 * 		public testONLY_100() {
 * 			...
 * 		}
 * 	}
 * </pre>
 * This test suite will have only test "testONLY_100" put in test suite while running it.
 * 
 * Note that these 2 mechanisms should be reset while executing "global" test suites.
 * For example:
 * <pre>
 * 	public class TestAll extends junit.framework.TestCase {
 * 		public TestAll(String testName) {
 * 			super(testName);
 * 		}
 * 		public static Test suite() {
 * 			TestCase.TESTS_PREFIX = null;
 * 			TestCase.TESTS_NAMES = null;
 * 			TestCase.TESTS_NUMBERS= null;
 * 			TestCase.TESTS_RANGE = null;
 * 			TestCase.RUN_ONLY_ID = null;
 * 			return buildTestSuite(MyTest.class);
 * 		}
 * 	}
 * </pre>
 * This will insure you that all tests will be put in TestAll test suite, even if static variables
 * values are set or some methods start as testONLY_...
 * 
 * @param evaluationTestClass the test suite class
 * @return a list ({@link List}) of tests ({@link Test}).
 */
public static List buildTestsList(Class evaluationTestClass) {
	return buildTestsList(evaluationTestClass, 0/*only one level*/, ORDERING);
}

/**
 * Build a list of methods to run for a test suite.
 * <br>
 * Differ from {@link #buildTestsList(Class)} in the fact that one
 * can specify level of recursion in hierarchy to find additional tests.
 * 
 * @param evaluationTestClass the test suite class
 * @param inheritedDepth level of recursion in top-level hierarchy to find other tests
 * @return a {@link List list} of {@link Test tests}.
 */
public static List buildTestsList(Class evaluationTestClass, int inheritedDepth) {
	return buildTestsList(evaluationTestClass, inheritedDepth, ORDERING);
}

/**
 * Build a list of methods to run for a test suite.
 * <br>
 * This list may be ordered in different ways using {@link #ORDERING}.
 * <br>
 * Example
 * <pre>
 * 	public class AbstractTest extends TestCase {
 * 		public MyTest(String name) {
 * 			super(name);
 * 		}
 * 		public testOne() {
 * 			...
 * 		}
 * 		public testTwo() {
 * 			...
 * 		}
 * 	}
 * 	public class MyTest extends AbstractTest {
 * 		public MyTest(String name) {
 * 			super(name);
 * 		}
 * 		public test001() {
 * 			...
 * 		}
 * 		public test002() {
 * 			...
 * 		}
 * 		...
 * 		public testONLY_100() {
 * 			...
 * 		}
 * 	}
 * </pre>
 * Returned list will have 5 tests if inheritedDepth is equals to 1 instead of
 * 3 if it was 0 as while calling by {@link #buildTestsList(Class)}.
 * 
 * @see #buildTestsList(Class) for complete explanation of subsets mechanisms.
 * 
 * @param evaluationTestClass the test suite class
 * @param inheritedDepth level of recursion in top-level hierarchy to find other tests
 * @param ordering kind of sort use for the list (see {@link #ORDERING} for possible values)
 * @return a {@link List list } of {@link Test tests}
 */
public static List buildTestsList(Class evaluationTestClass, int inheritedDepth, long ordering) {
	List tests = new ArrayList();
	List testNames = new ArrayList();
	List onlyNames = new ArrayList();
	Constructor constructor = null;
	try {
		// Get class constructor
		Class[] paramTypes = new Class[] { String.class };
		constructor = evaluationTestClass.getConstructor(paramTypes);
	}
	catch (Exception e) {
		// cannot get constructor, skip suite
		return tests;
	}

	// Get all tests from "test%" methods
	Method[] methods = evaluationTestClass.getDeclaredMethods();
	Class evaluationTestSuperclass = evaluationTestClass.getSuperclass();
	for (int i=0; i<inheritedDepth && !Flags.isAbstract(evaluationTestSuperclass.getModifiers()); i++) {
		Method[] superMethods = evaluationTestSuperclass.getDeclaredMethods();
		Method[] mergedMethods = new Method[methods.length+superMethods.length];
		System.arraycopy(superMethods, 0, mergedMethods, 0, superMethods.length);
		System.arraycopy(methods, 0, mergedMethods, superMethods.length, methods.length);
		methods = mergedMethods;
		evaluationTestSuperclass = evaluationTestSuperclass.getSuperclass();
	}

	// Build test names list
	final int methodPrefixLength = METHOD_PREFIX.length();
	nextMethod: for (int m = 0, max = methods.length; m < max; m++) {
		int modifiers = methods[m].getModifiers();
		if (Flags.isPublic(modifiers) && !Flags.isStatic(modifiers)) {
			String methName = methods[m].getName();
			if (methName.startsWith(METHOD_PREFIX)) {

				// look if this is a run only method
				boolean isOnly = RUN_ONLY_ID != null && methName.substring(methodPrefixLength).startsWith(RUN_ONLY_ID);
				if (isOnly) {
					if (!onlyNames.contains(methName)) {
						onlyNames.add(methName);
					}
					continue;
				}

				// no prefix, no subsets => add method
				if (TESTS_PREFIX == null && TESTS_NAMES == null && TESTS_NUMBERS == null && TESTS_RANGE == null) {
					if (!testNames.contains(methName)) {
						testNames.add(methName);
					}
					continue nextMethod;
				}

				// no prefix or method matches prefix
				if (TESTS_PREFIX == null || methName.startsWith(TESTS_PREFIX)) {
					int numStart = TESTS_PREFIX==null ? methodPrefixLength : TESTS_PREFIX.length();
					// tests names subset
					if (TESTS_NAMES != null) {
						for (int i = 0, imax= TESTS_NAMES.length; i<imax; i++) {
							if (methName.indexOf(TESTS_NAMES[i]) >= 0) {
								if (!testNames.contains(methName)) {
									testNames.add(methName);
								}
								continue nextMethod;
							}
						}
					}
					// look for test number
					int length = methName.length();
					if (numStart < length) {
						// get test number
						while (numStart<length && !Character.isDigit(methName.charAt(numStart))) numStart++; // skip to first digit
						while (numStart<length && methName.charAt(numStart) == '0') numStart++; // skip to first non-nul digit
						int n = numStart;
						while (n<length && Character.isDigit(methName.charAt(n))) n++; // skip to next non-digit
						if (n>numStart && n <= length) {
							try {
								int num = Integer.parseInt(methName.substring(numStart, n));
								// tests numbers subset
								if (TESTS_NUMBERS != null && !testNames.contains(methName)) {
									for (int i = 0; i < TESTS_NUMBERS.length; i++) {
										if (TESTS_NUMBERS[i] == num) {
											testNames.add(methName);
											continue nextMethod;
										}
									}
								}
								// tests range subset
								if (TESTS_RANGE != null && TESTS_RANGE.length == 2 && !testNames.contains(methName)) {
									if ((TESTS_RANGE[0]==-1 || num>=TESTS_RANGE[0]) && (TESTS_RANGE[1]==-1 || num<=TESTS_RANGE[1])) {
										testNames.add(methName);
										continue nextMethod;
									}
								}
							} catch (NumberFormatException e) {
								System.out.println("Method "+methods[m]+" has an invalid number format: "+e.getMessage());
							}
						}
					}

					// no subset, add all tests
					if (TESTS_NAMES==null && TESTS_NUMBERS==null && TESTS_RANGE==null) {
						if (!testNames.contains(methName)) {
							testNames.add(methName);
						}
					}
				}
			}
		}
	}

	// Order tests
	List names = onlyNames.size() > 0 ? onlyNames : testNames;
	if (ordering == ALPHA_REVERSE_SORT) {
		Collections.sort(names, Collections.reverseOrder());
	} else if (ordering == ALPHABETICAL_SORT) {
		Collections.sort(names);
	} else if (ordering != NO_ORDER) {
		Collections.shuffle(names, new Random(ordering));
	}

	// Add corresponding tests
	Iterator iterator = names.iterator();
	while (iterator.hasNext()) {
		String testName = (String) iterator.next();
		try {
			tests.add(constructor.newInstance(new Object[] { testName } ));
		}
		catch (Exception e) {
			System.err.println("Method "+testName+" removed from suite due to exception: "+e.getMessage());
		}
	}
	return tests;
}
/**
 * Build a test suite with all tests computed from public methods starting with "test"
 * found in the given test class.
 * Test suite name is the name of the given test class.
 * 
 * Note that this lis maybe reduced using some mechanisms detailed in {@link #buildTestsList(Class)} method.
 * 
 * @param evaluationTestClass
 * @return a {@link Test test suite} 
 */
public static Test buildTestSuite(Class evaluationTestClass) {
	return buildTestSuite(evaluationTestClass, null); //$NON-NLS-1$
}

/**
 * Build a test suite with all tests computed from public methods starting with "test"
 * found in the given test class.
 * Test suite name is the given name.
 * 
 * Note that this lis maybe reduced using some mechanisms detailed in {@link #buildTestsList(Class)} method.
 * 
 * @param evaluationTestClass
 * @param suiteName
 * @return a test suite ({@link Test}) 
 */
public static Test buildTestSuite(Class evaluationTestClass, String suiteName) {
	TestSuite suite = new TestSuite(suiteName==null?evaluationTestClass.getName():suiteName);
	List tests = buildTestsList(evaluationTestClass);
	for (int index=0, size=tests.size(); index<size; index++) {
		suite.addTest((Test)tests.get(index));
	}
	return suite;
}

/*
 * Shows the line separators in the given String.
 */
protected static String showLineSeparators(String string) {
	if (string == null) return null;
	StringBuffer buffer = new StringBuffer();
	int length = string.length();
	for (int i = 0; i < length; i++) {
		char car = string.charAt(i);
		switch (car) {
			case '\n': 
				buffer.append("\\n\n"); //$NON-NLS-1$
				break;
			case '\r':
				if (i < length-1 && string.charAt(i+1) == '\n') {
					buffer.append("\\r\\n\n"); //$NON-NLS-1$
					i++;
				} else {
					buffer.append("\\r\n"); //$NON-NLS-1$
				}
				break;
			default:
				buffer.append(car);
				break;
		}
	}
	return buffer.toString();
}
}
