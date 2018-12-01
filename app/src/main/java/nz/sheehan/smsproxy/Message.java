package nz.sheehan.smsproxy;

import org.json.JSONException;
import org.json.JSONObject;

public class Message {

    private String id;
    private String number;
    private String text;

    public Message(String id, String number, String text) {
        this.id = id;
        this.number = number;
        this.text = text;
    }

    @Override
    public String toString() {
        return String.format("#%s %s: \"%s\"", id, number, text);
    }

    public String getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public String getText() {
        return text;
    }

    public static Message fromJson(JSONObject json) throws JSONException {
        String id = json.getString("id");
        String text = json.getString("text");
        String number = json.getString("number");
        return new Message(id, number, text);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.accumulate("id", id);
        json.accumulate("number", number);
        json.accumulate("text", text);
        return json;
    }
}
