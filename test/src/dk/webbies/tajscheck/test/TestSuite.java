package dk.webbies.tajscheck.test;

import dk.webbies.tajscheck.test.dynamic.RunBenchmarks;
import dk.webbies.tajscheck.test.dynamic.TestUnderscore;
import dk.webbies.tajscheck.test.dynamic.UnitTests;
import dk.webbies.tajscheck.test.tajs.TAJSTests;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Created by erik1 on 24-11-2016.
 */
@RunWith(Suite.class)

@Suite.SuiteClasses({
        TestUnderscore.class,
        UnitTests.class,
        RunBenchmarks.class,
        TAJSTests.class
})
public class TestSuite {
}
