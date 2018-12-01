package nz.sheehan.smsproxy;

import android.content.Context;

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

public class SmsProxy {

    private String server;
    private RequestQueue queue;

    public SmsProxy(String server, Context context) {
        this.server = server;
        this.queue = Volley.newRequestQueue(context);
    }

    @Override
    public String toString() {
        return server;
    }

    public String getServer() {
        return server;
    }

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
        queue.add(new JsonArrayRequest(Request.Method.GET, server.concat("/outbox"), null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        ArrayList<Message> messages = new ArrayList<>();
                        for (int i = 0; i < response.length(); i++) {
                            try {
                                Message message = Message.fromJson((JSONObject)response.get(i));
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

    public void removeFromOutbox(String id, final NetworkCallback callback) {
        queue.add(new JsonObjectRequest(Request.Method.DELETE, server.concat("/outbox/".concat(id)), null,
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

}
