package agent;

import java.util.concurrent.atomic.AtomicLong;


public class MethodInfo {
    static final int MF_CONSTRUCTOR = 1;
    static final int MF_TRACEDCALL = 2;

    private final String className;
    private final String methodName;
    private int flags;

    private final AtomicLong totalTime = new AtomicLong();
    private final AtomicLong callCount = new AtomicLong();

    void addTime(long delta) { totalTime.addAndGet(delta); }
    void incrementCallCount()  { callCount.incrementAndGet(); }

    public boolean isConstructor() { return (flags & MF_CONSTRUCTOR) != 0; }
    public boolean isTraced() { return (flags & MF_TRACEDCALL) != 0; }

    void updateStats(MethodCallMarker marker) {
        addTime(System.nanoTime() - marker.startTime);
    }

    public MethodInfo(String className, String methodName, int flags) {
        this.className = className;
        this.methodName = methodName;
        this.flags = flags;
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public long getTotalTime() {
        return totalTime.get();
    }

    public long getCallCount() {
        return callCount.get();
    }
}
