package nz.sheehan.smsproxy;

public interface NetworkCallback {
    void onSuccess(Object result);
    void onFailure(Exception error);
}
