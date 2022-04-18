package team.genesis.android.activevoip.voip;

import androidx.annotation.Nullable;

import java.util.Comparator;
import java.util.PriorityQueue;

public abstract class FrameManager {
    public FrameManager(int tolerance) {
        this.tolerance = tolerance;
    }

    public static class Frame{
        short[] decoded;
        short[] predict;
        long counter;

        public Frame(short[] decoded, short[] predict, long counter) {
            this.decoded = decoded;
            this.predict = predict;
            this.counter = counter;
        }
    }

    protected final int tolerance;

    public abstract void push(Frame f);
    @Nullable public abstract Frame get();

}
