package nz.sheehan.smsproxy;

import java.util.Observable;

public class ObservableObject extends Observable {

    private static ObservableObject instance = new ObservableObject();

    public static ObservableObject getInstance() {
        return instance;
    }

    public void updateValue(Object data) {
        synchronized (this) {
            setChanged();
            notifyObservers(data);
        }
    }

}
