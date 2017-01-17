package dk.webbies.tajscheck.test.experiments;

import dk.webbies.tajscheck.CoverageResult;
import dk.webbies.tajscheck.Main;
import dk.webbies.tajscheck.OutputParser;
import dk.webbies.tajscheck.RunSmall;
import dk.webbies.tajscheck.util.Pair;
import dk.webbies.tajscheck.util.Util;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Created by erik1 on 16-01-2017.
 */
public class RunExperiments {
    private static final int TIMEOUT = 60 * 1000;
    private static final int THREADS = 3;
    private static final int RUN_SMALL_THREADS = 1;

    private static final Pair<String, Experiment.ExperimentSingleRunner> runSmall = new Pair<>("runSmall", (bench) -> {
        bench = bench.withOptions(bench.options.getBuilder().setCheckDepth(bench.options.checkDepth + 1).build());
        RunSmall.genSmallDrivers(bench, RUN_SMALL_THREADS);
        List<OutputParser.RunResult> results = RunSmall.runSmallDrivers(
                bench,
                RunSmall.runDriver(bench.run_method, TIMEOUT),
                THREADS
        );


        long paths = OutputParser.combine(results).typeErrors.stream().map(OutputParser.TypeError::getPath).distinct().count();

        return Long.toString(paths);
    });

    private static final Pair<String, Experiment.ExperimentSingleRunner> smallCoverage = new Pair<>("small-coverage", (bench) -> {
        bench = bench.withOptions(bench.options.getBuilder().setCheckDepth(bench.options.checkDepth + 1).build());
        RunSmall.genSmallDrivers(bench, RUN_SMALL_THREADS);
        List<CoverageResult> results = RunSmall.runSmallDrivers(
                bench,
                RunSmall.runCoverage(bench, TIMEOUT),
                THREADS
        );

        return Util.toPercentage(CoverageResult.combine(results).statementCoverage());
    });

    private static final Pair<String, Experiment.ExperimentSingleRunner> uniquePaths = new Pair<>("uniquePaths", (bench) -> {
        Main.writeFullDriver(bench);
        OutputParser.RunResult result;
        try {
            result = OutputParser.parseDriverResult(Main.runBenchmark(bench, TIMEOUT));
        } catch (TimeoutException e) {
            System.out.println("Timeout");
            return null;
        }
        long paths = result.typeErrors.stream().map(OutputParser.TypeError::getPath).distinct().count();
        return Long.toString(paths);
    });

    private static final Pair<List<String>, Experiment.ExperimentMultiRunner> uniquePathsConvergence = new Pair<>(Arrays.asList("uniquePaths", "uniquePathsConvergence", "iterationsUntilConvergence"), (bench) -> {
        Main.writeFullDriver(bench);
        OutputParser.RunResult result;
        try {
            result = OutputParser.parseDriverResult(Main.runBenchmark(bench, TIMEOUT));
        } catch (TimeoutException e) {
            System.out.println("Timeout");
            return Arrays.asList(null, null, null);
        }
        Set<String> paths = result.typeErrors.stream().map(OutputParser.TypeError::getPath).collect(Collectors.toSet());
        long firstPathCount = paths.size();
        System.out.println("Counted " + firstPathCount + " paths, trying to test again, to see if i get more");

        long prevCount = 0;
        long count = firstPathCount;
        int runs = 1;
        while (prevCount != count) {
            prevCount = count;
            try {
                result = OutputParser.parseDriverResult(Main.runBenchmark(bench, TIMEOUT));
                runs++;
            } catch (TimeoutException e) {
                System.out.println("Timeout");
                return Arrays.asList(Long.toString(firstPathCount), Long.toString(count), Integer.toString(runs));
            }
            result.typeErrors.stream().map(OutputParser.TypeError::getPath).forEach(paths::add);
            count = paths.size();
            System.out.println("Previously had " + prevCount + " paths, now i have seen " + count);
        }


        return Arrays.asList(Long.toString(firstPathCount), Long.toString(count), Integer.toString(runs));
    });

    private static final Pair<List<String>, Experiment.ExperimentMultiRunner> uniquePathsAndCoverage = new Pair<>(Arrays.asList("uniquePaths", "coverage"), (bench) -> {
        String uniquePaths = RunExperiments.uniquePaths.getRight().run(bench);
        if (uniquePaths == null) {
            return Arrays.asList(null, null);
        }

        Map<String, CoverageResult> out;
        try {
            out = Main.genCoverage(bench, TIMEOUT * 5); // <- More timeout
        } catch (TimeoutException e) {
            // this is ok, it happens.
            System.out.println("Timeout on coverage");
            return Arrays.asList(uniquePaths, null);
        }

        assert out.containsKey(bench.getJSName());

        return Arrays.asList(uniquePaths, Util.toPercentage(out.get(bench.getJSName()).statementCoverage()));
    });

    private static final Pair<List<String>, Experiment.ExperimentMultiRunner> uniquePathsAnd5Coverage = new Pair<>(Arrays.asList("uniquePaths", "5coverage"), (bench) -> {
        String uniquePaths = RunExperiments.uniquePaths.getRight().run(bench);
        if (uniquePaths == null) {
            return Arrays.asList(null, null);
        }

        Map<String, CoverageResult> out = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            try {
                Map<String, CoverageResult> subResult = Main.genCoverage(bench, TIMEOUT * 5);
                out = CoverageResult.combine(out, subResult);
            } catch (TimeoutException e) {
                // this is ok, it happens.
                System.out.println("Timeout on coverage");
                return Arrays.asList(uniquePaths, null);
            }
        }

        assert out.containsKey(bench.getJSName());

        return Arrays.asList(uniquePaths, Util.toPercentage(out.get(bench.getJSName()).statementCoverage()));
    });

    private static final Pair<List<String>, Experiment.ExperimentMultiRunner> driverSizes = new Pair<>(Arrays.asList("size", "size-no-generics"), (bench) -> {
        double DIVIDE_BY = 1000 * 1000;
        String SUFFIX = "mb";
        int DECIMALS = 1;

        String fullSize = null;
        if (!bench.options.disableGenerics) {
            fullSize = Util.toFixed(Main.generateFullDriver(bench).length() / DIVIDE_BY, DECIMALS) + SUFFIX;
        }

        double noGenerics = Main.generateFullDriver(bench.withOptions(bench.options.getBuilder().setDisableGenerics(true).build())).length() / DIVIDE_BY;

        return Arrays.asList(fullSize, Util.toFixed(noGenerics, DECIMALS) + SUFFIX);
    });

    private static final Pair<String, Experiment.ExperimentSingleRunner> jsFileSize = new Pair<>("jsFileSize", (bench) -> {
        double DIVIDE_BY = 1000 * 1000;
        String SUFFIX = "mb";
        int DECIMALS = 1;

        return Util.toFixed(Util.readFile(bench.jsFile).length() / DIVIDE_BY, DECIMALS) + SUFFIX;
    });

    @Test
    public void runExperiment() throws Exception {
        Experiment experiment = new Experiment("Moment.js");

        experiment.addSingleExperiment(smallCoverage);
        experiment.addSingleExperiment(runSmall);

//        experiment.addSingleExperiment(uniquePaths);
//        experiment.addMultiExperiment(uniquePathsAndCoverage);
//        experiment.addMultiExperiment(uniquePathsAnd5Coverage);
//        experiment.addMultiExperiment(uniquePathsConvergence);


//        experiment.addMultiExperiment(driverSizes);
//        experiment.addSingleExperiment(jsFileSize);


        String result = experiment.calculate(THREADS).toCSV();
        System.out.println("\n\n\nResult: \n");
        System.out.println(result);

    }
}
