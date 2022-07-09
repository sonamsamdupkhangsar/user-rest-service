package me.sonam.user.handler;

/**
 * this is for parsing the data from serverrequest body to this object
 */
public class AuthTransfer {
    private String authenticationId;
    private String password;
    private String apiKey;

    public AuthTransfer() {

    }
    public AuthTransfer(String authenticationId, String password, String apiKey) {
        this.authenticationId = authenticationId;
        this.password = password;
        this.apiKey = apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAuthenticationId() {
        return authenticationId;
    }

    public void setAuthenticationId(String authenticationId) {
        this.authenticationId = authenticationId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
