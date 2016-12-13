package dk.webbies.tajscheck.benchmarks;

/**
 * Created by erik1 on 13-12-2016.
 */
public final class CheckOptions {
    public final int checkDepth; // TODO: Test incrementing this one.
    public final int checkDepthForUnions; // TODO: Test incrementing this to two.
    public final boolean checkHeap;
    private CheckOptions(Builder builder) {
        this.checkDepth = builder.checkDepth;
        this.checkDepthForUnions = builder.checkDepthForUnions;
        this.checkHeap = builder.checkHeap;
    }

    public static CheckOptions defaultOptions() {
        return new Builder().build();
    }

    public static final class Builder {
        private int checkDepth = 0;
        private int checkDepthForUnions = 1;
        public boolean checkHeap = false;

        private CheckOptions build() {
            return new CheckOptions(this);
        }
    }
}