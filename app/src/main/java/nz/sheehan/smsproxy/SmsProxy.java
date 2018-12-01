package nz.sheehan.smsproxy;

import android.content.Context;
import android.util.Log;

import com.android.volley.Network;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class SmsProxy {

    private String server;
    private String device;
    private RequestQueue queue;

    public SmsProxy(Context context, String server, String device) {
        this.server = server;
        this.device = device;
        this.queue = Volley.newRequestQueue(context);
    }

    @Override
    public String toString() {
        return String.format("%s@%s", device, server);
    }

    public String getServer() {
        return server;
    }

    public String getDevice() { return device; }

    private String getDeviceUri() { return String.format("%s/devices/%s", server, device); }

    public void getVersion(final NetworkCallback callback) {

        queue.add(new JsonObjectRequest(Request.Method.GET, server.concat("/"), null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        try {
                            String version = response.getString("version");
                            callback.onSuccess(version);
                        } catch (JSONException ex) {
                            callback.onFailure(ex);
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callback.onFailure(error);
                    }
                }));

    }

    public void getOutbox(final NetworkCallback callback) {
        queue.add(new JsonObjectRequest(Request.Method.GET, getDeviceUri().concat("/outbox"), null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        ArrayList<Message> messages = new ArrayList<>();
                        Iterator<String> keys = response.keys();
                        while (keys.hasNext()) {
                            String id = keys.next();
                            try {
                                Message message = Message.fromJson((JSONObject)response.get(id));
                                messages.add(message);
                            } catch (JSONException ex) {
                                callback.onFailure(ex);
                            }
                        }
                        callback.onSuccess(messages);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callback.onFailure(error);
                    }
                }));
    }

    public void removeFromOutbox(final String id, final NetworkCallback callback) {
        queue.add(new JsonObjectRequest(Request.Method.DELETE, getDeviceUri().concat("/outbox/".concat(id)), null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        callback.onSuccess(null);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        callback.onFailure(error);
                    }
                }));
    }

    public void addToInbox(String number, String text, final NetworkCallback callback) {
        JSONObject json = new JSONObject();
        try {
            json.accumulate("number", number);
            json.accumulate("text", text);
            queue.add(new JsonObjectRequest(Request.Method.POST, getDeviceUri().concat("/inbox"), json,
                    new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            callback.onSuccess(null);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            callback.onFailure(error);
                        }
                    }));
        } catch (JSONException ex) {
            callback.onFailure(ex);
        }
    }

}
