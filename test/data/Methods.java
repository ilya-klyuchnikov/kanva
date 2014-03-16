package data;

public class Methods {
    public void test01(Object o1, @ShouldBeCollected Object o2, int i) {
        if (i > 0) {
            o1.hashCode();
        }
        o2.hashCode();
    }

    public void test02(Object o1, Object o2, int i) {
        if (i > 0) {
            o1.hashCode();
        } else {
            o2.hashCode();
        }
    }

    public boolean test03(@ShouldBeCollected Object[] os, Object o) {
        for (Object o1 : os) {
            if (o.hashCode() == o1.hashCode()) {
                return true;
            }
        }
        return false;
    }
}
