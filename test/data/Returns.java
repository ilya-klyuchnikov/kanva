package data;

public class Returns {
    public Object simpleFor(int size) {
        int[] result = new int[size];
        for (int i = 0; i < size; i++) {
            result[i] = i;
        }
        return result;
    }

    public String stringConstant() {
        return "";
    }
}
