package me.sonam.user.email;

/**
 * A model to represent email data
 */
public class Email {
    private String from;
    private String to;
    private String subject;
    private String body;

    private String emailRegEx = "^(.+)@(.+)$";

    public Email(String from, String to, String subject, String body) {
        this.from = from;
        this.to = to;
        this.subject = subject;
        this.body = body;
    }

    public void validate() {
        if (!this.from.matches(emailRegEx)) {
            throw new RuntimeException("Invalid 'from' email");
        }
        if (!this.to.matches(emailRegEx)) {
            throw new RuntimeException("Invalid 'to' email");
        }
        if (this.subject == null) {
            this.subject = "";
        }
        if (this.body == null) {
            this.body = "";
        }
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getBody() {
        return body;
    }

    public String getSubject() {
        return this.subject;
    }

    @Override
    public String toString() {
        return "Email{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", subject='" + subject + '\'' +
                ", body='" + body + '\'' +
                '}';
    }
}
