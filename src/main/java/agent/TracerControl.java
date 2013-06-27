package agent;

import reports.PerformanceReport;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;


enum TracerControlSingleton {
    D;

    private TracerControlSingleton() { tracerControl = new TracerControl(); }
    private final TracerControl tracerControl;

    TracerControl getInstance() { return tracerControl; }
}


class TracerControl {
    private Pattern packagePattern;
    private Pattern tracedPattern;
    private int debugLevel;
    private final Map<Long, MethodInfo> methods = new ConcurrentHashMap<>();
    private final AtomicLong methodIdSeq = new AtomicLong(0);
    private final MethodCallProcessor methodCallProcessor = new MethodCallProcessor();

    void init(String argString) {
        String args[] = argString.split("#");

        System.out.println("running with args:[" + argString + "]");

        for(String arg: args) {
            String kv[] = arg.split(":");
            if(kv == null && (kv[0] == null || kv[1] == null)) { continue; }
            switch (kv[0]) {
                case "p":
                    packagePattern = Pattern.compile(kv[1]);
                    System.out.println("package pattern:[" + kv[1] + "]");
                break;
                case "t":
                    tracedPattern = Pattern.compile(kv[1]);
                    System.out.println("trace call pattern:[" + kv[1] + "]");
                    break;
                case "d":
                    debugLevel = Integer.parseInt(kv[1]);
                    System.out.println("debug level:[" + debugLevel + "]");
            }
        }
    }

    boolean allowInstrumentClass(String className) {
        return packagePattern != null && packagePattern.matcher(className).matches();
    }

    boolean allowInstrumentMethod(String methodName) {
        return packagePattern != null && packagePattern.matcher(methodName).find();
    }

    boolean allowTraceMethod(String methodName) {
        return tracedPattern != null && tracedPattern.matcher(methodName).find();
    }


    long registerMethod(MethodInfo methodInfo) {
        long rvalue = methodIdSeq.getAndIncrement();
        methods.put(rvalue, methodInfo);

        return rvalue;
    }

    int getDebugLevel() {
        return debugLevel;
    }

    void methodEnter(MethodInfo methodInfo) {
        if(debugLevel >= 2) {
            debug(2, "entering method [" + Thread.currentThread().getId() + "]: " +
                    methodInfo.getMethodName());
        }
        methodCallProcessor.methodEnter(methodInfo);
    }

    void methodExit() {
        methodCallProcessor.methodExit();
    }

    void debug(int level, String message) {
        if(getDebugLevel() >= level) {
            System.out.println(message);
        }

    }

    void printStats() {
        PerformanceReport pr = new PerformanceReport(methods.values());

        pr.reportTopTimeConsumed(10);
        pr.reportTopCalled(10);
        pr.reportTopConstructed(10);
    }

    MethodInfo getMethodInfo(long id) { return methods.get(id); }
}
