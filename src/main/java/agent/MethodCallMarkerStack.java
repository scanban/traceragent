package agent;

import java.util.Vector;

class MethodCallMarkerStack {
    private int maxElement = -1;
    private int sp = -1;
    private final Vector<MethodCallMarker> stack = new Vector<>(1024, 256);

    void push(MethodInfo methodInfo) {
        if(++sp > maxElement) { stack.add(new MethodCallMarker()); ++maxElement; }
        MethodCallMarker m = stack.get(sp);
        m.methodInfo = methodInfo;
        m.startTime = MethodInfo.getTime();
    }

    MethodCallMarker pop() { return stack.get(sp--); }

    MethodCallMarker[] toArray() {
        MethodCallMarker[] m = new MethodCallMarker[sp + 1];
        for(int i = 0; i <= sp; ++i) { m[i] = stack.get(i); }

        return m;
    }
}
