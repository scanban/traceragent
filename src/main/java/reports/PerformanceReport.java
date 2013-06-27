package reports;

import agent.MethodInfo;

import java.util.*;


public class PerformanceReport {
    private final Collection<MethodInfo> methods;

    public PerformanceReport(Collection<MethodInfo> methods) {
        this.methods = methods;
    }

    public void reportTopCalled(int count) {
        MethodInfo[] report = methods.toArray(new MethodInfo[methods.size()]);

        Arrays.sort(report, new Comparator<MethodInfo>() {
            @Override
            public int compare(MethodInfo o1, MethodInfo o2) {
                return  Long.signum(o2.getCallCount() - o1.getCallCount());
            }
        });

        System.out.println("\nTop " + count + " called methods:");
        for(int i = 0; i < count && i < report.length; ++i) {
            if(report[i].getCallCount() == 0) { continue; }
            System.out.println(String.format("%16d %s",
                    report[i].getCallCount(),
                    report[i].getMethodName()));
        }
    }

    public void reportTopTimeConsumed(int count) {
        MethodInfo[] report = methods.toArray(new MethodInfo[methods.size()]);

        Arrays.sort(report, new Comparator<MethodInfo>() {
            @Override
            public int compare(MethodInfo o1, MethodInfo o2) {
                return Long.signum(o2.getTotalTime() - o1.getTotalTime());
            }
        });

        System.out.println("\nTop "+ count + " time consuming methods (microseconds):");
        for(int i = 0; i < count && i < report.length; ++i) {
            if(report[i].getCallCount() == 0) { continue; }
            System.out.println(String.format("%16d  %s",
                    report[i].getTotalTime() / 1000,
                    report[i].getMethodName()));
        }
    }

    public void reportTopConstructed(int count) {

        class Tuple {
            public long l;
            public final String s;

            Tuple(long l, String s) {
                this.l = l;
                this.s = s;
            }
        }

        System.out.println("\nTop "+ count + " objects constructed:");

        Map<String, Tuple> cobjects = new HashMap<>();
        for(MethodInfo m: methods) {
            if(!m.isConstructor() || m.getCallCount() == 0) { continue; }

            if(cobjects.containsKey(m.getClassName())) {
                cobjects.get(m.getClassName()).l += m.getCallCount();
            } else {
                cobjects.put(m.getClassName(),
                        new Tuple(m.getCallCount(), m.getClassName()));
            }
        }

        Tuple[] reportTuples = cobjects.values().toArray(new Tuple[cobjects.size()]);
        Arrays.sort(reportTuples, new Comparator<Tuple>() {
            @Override
            public int compare(Tuple o1, Tuple o2) {
                return Long.signum(o2.l - o1.l);
            }
        });

        for(int i = 0; i < count && i < reportTuples.length; ++i) {
            System.out.println(String.format("%16d %s",reportTuples[i].l,
                    reportTuples[i].s));
        }

    }

}