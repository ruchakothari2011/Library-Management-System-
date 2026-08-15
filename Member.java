import java.sql.Date;

public class Member {
    private int memberId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private Date joinDate;
    private String status;

    public Member() {
    }

    public Member(int memberId, String name, String email, String phone,
                  String address, Date joinDate, String status) {
        this.memberId = memberId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.joinDate = joinDate;
        this.status = status;
    }

    // Getters and setters
    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Date getJoinDate() { return joinDate; }
    public void setJoinDate(Date joinDate) { this.joinDate = joinDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return String.format(
                "ID: %-4d | %-20s | %-25s | %-12s | Joined: %s | %s",
                memberId, name, email, phone, joinDate, status);
    }
}
