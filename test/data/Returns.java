package data;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

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

    public Object instanceOf1(Object o) {
        if (o instanceof String) {
            return o;
        } else {
            throw new NullPointerException();
        }
    }

    public String instanceOf2(Object o) {
        if (!(o instanceof String)) {
            throw new NullPointerException();
        } else {
            return (String)o;
        }
    }

    public static JAXBContext newInstance(Class[] classes,
                                   Map properties,
                                   Class spFactory) throws JAXBException {
        Method m;
        try {
            m = spFactory.getMethod("createContext", Class[].class, Map.class);
        } catch (NoSuchMethodException e) {
            throw new JAXBException(e);
        }
        try {
            Object context = m.invoke(null, classes, properties);
            if(!(context instanceof JAXBContext)) {
                // the cast would fail, so generate an exception with a nice message
                throw new NullPointerException();
            }
            return (JAXBContext)context;
        } catch (IllegalAccessException e) {
            throw new JAXBException(e);
        } catch (InvocationTargetException e) {
            throw new NullPointerException();
        }
    }
}
