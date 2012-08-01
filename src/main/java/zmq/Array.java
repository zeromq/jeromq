package zmq;

import java.util.List;

public class Array {

	public static <T> void swap(List<T> items, int index1_, int index2_) {
		T item1 = items.get(index1_);
		T item2 = items.get(index2_);
        if (item1 != null)
        	items.set(index2_, item1);
        if (item2 != null)
        	items.set(index1_, item2);
	}

}
