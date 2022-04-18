package team.genesis.android.activevoip.voip;

import androidx.annotation.Nullable;

import java.util.PriorityQueue;

public class RawFrameManager extends FrameManager{

    protected final PriorityQueue<Frame> buf;
    protected long localCtrNext;

    public RawFrameManager(int tolerance) {
        super(tolerance);
        this.buf = new PriorityQueue<>((o1, o2) -> Long.compare(o1.counter,o2.counter));
        this.localCtrNext = 0;
    }

    @Override
    public void push(Frame f) {
        buf.add(f);
    }

    @Nullable
    @Override
    public Frame get() {
        if(buf.size()<1)    return null;
        while (buf.peek().counter<localCtrNext) buf.poll();
        if(buf.size()>tolerance)    buf.poll();
        if(buf.size()<1)    return null;
        localCtrNext = buf.peek().counter+1;
        return buf.poll();
    }
}
