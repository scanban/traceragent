package agent;


class MethodCallProcessor {
    private static final ThreadLocal<MethodCallMarkerStack> callStack =
            new ThreadLocal<MethodCallMarkerStack> () {
                @Override
                protected MethodCallMarkerStack initialValue() {
                    return new MethodCallMarkerStack();
                }
            };

    void methodEnter(MethodInfo methodInfo) {

        if(methodInfo.isTraced()) {
            System.out.println(String.format("[T][%6d] %s",
                    Thread.currentThread().getId(), methodInfo.getMethodName()));

            MethodCallMarker[] stack = callStack.get().toArray();

            for(int i = stack.length - 1; i >= 0; --i) {
                System.out.println(String.format("      %5d %s", i,
                        stack[i].methodInfo.getMethodName()));
            }
            System.out.println();
        }

        methodInfo.incrementCallCount();
        callStack.get().push(methodInfo);
    }

    void methodExit(MethodInfo methodInfo) {
        MethodCallMarker marker = callStack.get().pop();
        marker.methodInfo.updateStats(marker);
    }

    void methodExit() {
        MethodCallMarker marker = callStack.get().pop();
        marker.methodInfo.updateStats(marker);
    }

}
