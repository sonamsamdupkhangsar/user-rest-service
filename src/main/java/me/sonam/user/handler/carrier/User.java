package me.sonam.user.handler.carrier;

import java.util.Objects;
import java.util.UUID;

public class User {
    private UUID id;
    private String firstName;
    private String lastName;
    private String email;
    private String authenticationId;
    private Boolean active;
    private Boolean userAuthAccountCreated;
    private Boolean searchable;
    private String profilePhoto;

    public User() {
    }

    public User(UUID id, String firstName, String lastName, String email, String authenticationId, Boolean active,
                Boolean userAuthAccountCreated, Boolean searchable, String profilePhoto) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.authenticationId = authenticationId;
        this.active = active;
        this.userAuthAccountCreated = userAuthAccountCreated;
        this.searchable = searchable;
        this.profilePhoto = profilePhoto;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        User user = (User) object;
        return Objects.equals(id, user.id) && Objects.equals(firstName, user.firstName) && Objects.equals(lastName, user.lastName) && Objects.equals(email, user.email) && Objects.equals(authenticationId, user.authenticationId) && Objects.equals(active, user.active) && Objects.equals(userAuthAccountCreated, user.userAuthAccountCreated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, firstName, lastName, email, authenticationId, active, userAuthAccountCreated);
    }

    public Boolean getSearchable() {
        return searchable;
    }

    public void setSearchable(Boolean searchable) {
        this.searchable = searchable;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
        this.email = email;
    }

    public String getAuthenticationId() {
        return authenticationId;
    }

    public void setAuthenticationId(String authenticationId) {
        this.authenticationId = authenticationId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getUserAuthAccountCreated() {
        return userAuthAccountCreated;
    }

    public void setUserAuthAccountCreated(Boolean userAuthAccountCreated) {
        this.userAuthAccountCreated = userAuthAccountCreated;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public void setProfilePhoto(String profilePhoto) {
        this.profilePhoto = profilePhoto;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", authenticationId='" + authenticationId + '\'' +
                ", active=" + active +
                ", userAuthAccountCreated=" + userAuthAccountCreated +
                ", searchable=" + searchable +
                ", profilePhoto='" + profilePhoto + '\'' +
                '}';
    }
}
