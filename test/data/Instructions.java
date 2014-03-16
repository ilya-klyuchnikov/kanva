package data;

public class Instructions {

    int i;

    public void invokeMethod(Object o1) {
        o1.toString();
    }

    public int arrayLength(
            int[] is,
            boolean[] bs,
            long[] ls,
            short[] ss,
            char[] cs,
            double[] ds,
            float[] fs,
            Object[] os) {
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
            int[] is,
            boolean[] bs,
            long[] ls,
            short[] ss,
            char[] cs,
            double[] ds,
            float[] fs,
            Object[] os

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
            int[] is,
            boolean[] bs,
            long[] ls,
            short[] ss,
            char[] cs,
            double[] ds,
            float[] fs,
            Object[] os

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

    public int monitor(Object o) {
        synchronized (o) {
            return 1;
        }
    }

    public void fields(Instructions i1, Instructions i2) {
        i1.i = i2.i;
    }

}
