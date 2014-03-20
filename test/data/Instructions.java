package data;

public class Instructions {

    int i;

    public void invokeMethod(
            @ShouldBeCollected Object o1
    ) {
        o1.toString();
    }

    public int arrayLength(
            @ShouldBeCollected int[] is,
            @ShouldBeCollected boolean[] bs,
            @ShouldBeCollected long[] ls,
            @ShouldBeCollected short[] ss,
            @ShouldBeCollected char[] cs,
            @ShouldBeCollected double[] ds,
            @ShouldBeCollected float[] fs,
            @ShouldBeCollected Object[] os
    ) {
        return is.length +
                bs.length +
                ls.length +
                ss.length +
                cs.length +
                ds.length +
                fs.length +
                os.length;
    }

    public String arrayLoad(
            @ShouldBeCollected int[] is,
            @ShouldBeCollected boolean[] bs,
            @ShouldBeCollected long[] ls,
            @ShouldBeCollected short[] ss,
            @ShouldBeCollected char[] cs,
            @ShouldBeCollected double[] ds,
            @ShouldBeCollected float[] fs,
            @ShouldBeCollected Object[] os
    ) {
        return "" +
                is[0] +
                bs[0] +
                ls[0] +
                ss[0] +
                cs[0] +
                ds[0] +
                fs[0] +
                os[0];
    }

    public void arrayStore(
            @ShouldBeCollected int[] is,
            @ShouldBeCollected boolean[] bs,
            @ShouldBeCollected long[] ls,
            @ShouldBeCollected short[] ss,
            @ShouldBeCollected char[] cs,
            @ShouldBeCollected double[] ds,
            @ShouldBeCollected float[] fs,
            @ShouldBeCollected Object[] os
    ) {
        is[0] = 0;
        bs[0] = false;
        ls[0] = 0;
        ss[0] = 0;
        cs[0] = 0;
        ds[0] = 0;
        fs[0] = 0;
        os[0] = null;
    }

    public int monitor(
            @ShouldBeCollected Object o
    ) {
        synchronized (o) {
            return 1;
        }
    }

    public void fields(
            @ShouldBeCollected Instructions i1,
            @ShouldBeCollected Instructions i2
    ) {
        i1.i = i2.i;
    }

    //// branching
    public void pureBranching01(Object o1, @ShouldBeCollected Object o2, int i) {
        if (i > 0) {
            o1.hashCode();
        }
        o2.hashCode();
    }

    public void pureBranching02(Object o1, Object o2, int i) {
        if (i > 0) {
            o1.hashCode();
        } else {
            o2.hashCode();
        }
    }

    public boolean pureBranching03(@ShouldBeCollected Object[] os, Object o) {
        for (Object o1 : os) {
            if (o.hashCode() == o1.hashCode()) {
                return true;
            }
        }
        return false;
    }


    public void exceptions1(Object o1, Object o2) {
        if (o1 == null) {
            throw new NullPointerException("xx");
        }
        if (o2 == null) {
            throw new NullPointerException("yy");
        }
    }

    public native Object test();

    public Instructions exceptions2(Instructions r) {
        Object dummy = test();
        if (dummy != null) {
            r.i = 1;
        } else if (r == null) {
            throw new NullPointerException("null parameter");
        }
        return r;
    }

    public void instanceOfException1(Object o) {
        if (o instanceof String) {
            return;
        }
        throw new IllegalArgumentException("xxx");
    }

    public void instanceOfException2(Object o) {
        if (!(o instanceof String)) {
            throw new IllegalArgumentException("xxx");
        }

    }
}
