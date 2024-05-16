package me.sonam.user.repo.entity;


import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * represents a Account record in Account table.
 */

public class MyUser implements Persistable<UUID> {

    @Id
    private UUID id;
    // on user signup only firstname, lastname and email is required
    private String firstName;
    private String lastName;
    private String email;
    private String authenticationId;
    private Boolean active;
    private Boolean userAuthAccountCreated;

    public Boolean getSearchable() {
        return searchable;
    }

    public void setSearchable(Boolean searchable) {
        this.searchable = searchable;
    }

    private Boolean searchable;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    // these can be populated after login
    private LocalDate birthDate;
    private String profilePhoto; //will contain the full url to the profilephoto
    private UUID genderId;

    @Transient
    private boolean newAccount;

    public MyUser() {
    }

    public MyUser(String firstName, String lastName, String email, String authenticationId) {
        this.id = UUID.randomUUID();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.authenticationId = authenticationId;
        this.active = false;
        this.userAuthAccountCreated = false;
        this.newAccount = true;
    }

    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return this.newAccount;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getProfilePhoto() {
        return profilePhoto;
    }

    public UUID getGenderId() {
        return genderId;
    }

    public boolean isNewAccount() {
        return newAccount;
    }

    public void setNewAccount(boolean newAccount) {
        this.newAccount = newAccount;
    }

    public String getAuthenticationId() {
        return this.authenticationId;
    }

    public void setUserAuthAccountCreated(boolean userAuthAccountCreated) {
        this.userAuthAccountCreated = userAuthAccountCreated;
    }

    public boolean getUserAuthAccountCreated() {
        return this.userAuthAccountCreated;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", email='" + email + '\'' +
                ", birthDate=" + birthDate +
                ", profilePhoto='" + profilePhoto + '\'' +
                ", genderId=" + genderId +
                ", newAccount=" + newAccount +
                '}';
    }


    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (this.getClass() != obj.getClass()) {
            return false;
        } else {
            MyUser other = (MyUser)obj;
            if (this.birthDate == null) {
                if (other.birthDate != null) {
                    return false;
                }
            } else if (!this.birthDate.equals(other.birthDate)) {
                return false;
            }

            if (this.email == null) {
                if (other.email != null) {
                    return false;
                }
            } else if (!this.email.equals(other.email)) {
                return false;
            }

            if (this.firstName == null) {
                if (other.firstName != null) {
                    return false;
                }
            } else if (!this.firstName.equals(other.firstName)) {
                return false;
            }

            if (this.id == null) {
                if (other.id != null) {
                    return false;
                }
            } else if (!this.id.equals(other.id)) {
                return false;
            }

            if (this.lastName == null) {
                if (other.lastName != null) {
                    return false;
                }
            } else if (!this.lastName.equals(other.lastName)) {
                return false;
            }

            return true;
        }
    }

}