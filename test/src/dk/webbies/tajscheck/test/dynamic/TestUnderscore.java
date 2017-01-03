package dk.webbies.tajscheck.test.dynamic;

import dk.webbies.tajscheck.Main;
import dk.webbies.tajscheck.benchmarks.Benchmark;
import dk.webbies.tajscheck.benchmarks.CheckOptions;
import dk.webbies.tajscheck.parsespec.ParseDeclaration;
import org.junit.Ignore;
import org.junit.Test;

import static dk.webbies.tajscheck.benchmarks.Benchmark.LOAD_METHOD.LOAD_LOCAL;

/**
 * Created by erik1 on 22-11-2016.
 */
public class TestUnderscore {

    @Ignore
    @Test
    public void genFullUnderscoreDriver() throws Exception {
        Benchmark underscore = new Benchmark(ParseDeclaration.Environment.ES5Core, "test/underscore/underscore.js", "test/underscore/underscore.d.ts", "_", LOAD_LOCAL, CheckOptions.defaultOptions());

        Main.writeFullDriver(underscore);

    }
}
