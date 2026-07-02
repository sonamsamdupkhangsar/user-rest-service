package me.sonam.user.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProfilePhotoUrl {
    private static final Logger LOG = LoggerFactory.getLogger(ProfilePhotoUrl.class);

    public static String getProfileUrl(String profilePhotoJson) {
        LOG.debug("profile photo metadata received");

        if (profilePhotoJson == null || profilePhotoJson.isEmpty()) {
            LOG.info("profilePhoto json is empty or null, return empty string");
            return "";
        }
        try {
            JsonElement jsonElement = JsonParser.parseString(profilePhotoJson);
            LOG.debug("profile photo metadata type: {}", jsonElement.getClass());

            JsonObject jsonObject2 = null;
            if (jsonElement.isJsonPrimitive()) {
                JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
                // Get the primitive value (string, number, boolean)
                JsonElement jsonElement2 = JsonParser.parseString(jsonPrimitive.getAsString());
                LOG.info("jsonPrimitive to jsonElement.isJsonObject ?: {}", jsonElement2.isJsonObject());

                jsonObject2 = jsonElement2.getAsJsonObject();
                final String thumbnailUrl = jsonObject2.get("thumbnailUrl").getAsString();
                LOG.debug("thumbnail URL extracted from profile photo metadata");
                return thumbnailUrl;
            } else if (jsonElement.isJsonObject()) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                final String thumbnailUrl = jsonObject.get("thumbnailUrl").getAsString();
                LOG.debug("thumbnail URL extracted from profile photo metadata");
                return thumbnailUrl;
            } else {
                return "empty";
            }
        }
        catch (Exception e) {
            LOG.error("profilePhoto json is not in valid format: {}", e.getMessage());
            LOG.info("exception stack trace is", e);
            return "";
        }
    }

}
