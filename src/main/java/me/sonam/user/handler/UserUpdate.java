package me.sonam.user.handler;

/**
 * this is for parsing the data from serverrequest body to this object
 */
public class UserUpdate {
    private String firstName;
    private String lastName;
    private String email;
    private String authenticationId;
    private String password;
    private boolean searchable;
    private String profilePhoto;

    public UserUpdate() {

    }
    public UserUpdate(String firstName, String lastName, String email, String authenticationId, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        if (email != null) {
            this.email = email.toLowerCase();
        }

        if (authenticationId != null) {
            this.authenticationId = authenticationId.toLowerCase();
        }
        this.password = password;
    }
    public String getAuthenticationId() {
        return authenticationId;
    }

    public void setAuthenticationId(String authenticationId) {
        if (authenticationId != null) {
            this.authenticationId = authenticationId.toLowerCase();
        }
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        if (email != null) {
            this.email = email.toLowerCase();
        }
    }

    public boolean isSearchable() {
        return searchable;
    }

    public void setSearchable(boolean searchable) {
        this.searchable = searchable;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public void setProfilePhoto(String profilePhoto) {
        this.profilePhoto = profilePhoto;
    }

    @Override
    public String toString() {
        return "UserTransfer{" +
                "firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", authenticationId='" + authenticationId + '\'' +
                ", password='" + password + '\'' +
                ", searchable='" + searchable + '\'' +
                ", profilePhoto='" + profilePhoto + '\'' +
                '}';
    }
}
