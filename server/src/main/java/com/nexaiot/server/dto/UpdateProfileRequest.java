package com.nexaiot.server.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {

    @Size(max = 60, message = "First name must be 60 characters or fewer")
    private String firstName;

    @Size(max = 60, message = "Last name must be 60 characters or fewer")
    private String lastName;

    @Size(max = 30, message = "Phone number must be 30 characters or fewer")
    @Pattern(regexp = "(?=.*\\d)^\\s*\\+?[0-9()\\-\\s]{7,30}\\s*$", message = "Phone number contains invalid characters")
    private String phoneNumber;

    @Size(max = 100, message = "Job title must be 100 characters or fewer")
    private String jobTitle;

    @Size(max = 100, message = "Department must be 100 characters or fewer")
    private String department;

    @Size(max = 500, message = "Bio must be 500 characters or fewer")
    private String bio;

    @Size(max = 100, message = "Time zone must be 100 characters or fewer")
    private String timeZone;

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getJobTitle() { return jobTitle; }
    public String getDepartment() { return department; }
    public String getBio() { return bio; }
    public String getTimeZone() { return timeZone; }

    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public void setDepartment(String department) { this.department = department; }
    public void setBio(String bio) { this.bio = bio; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
}
