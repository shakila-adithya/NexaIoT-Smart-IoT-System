package com.nexaiot.server.dto;

import com.nexaiot.server.model.User;

import java.time.Instant;

public class UserResponse {

    private String id;
    private String fullName;
    private String email;
    private String role;
    private boolean hasProfileImage;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String jobTitle;
    private String department;
    private String bio;
    private String timeZone;
    private Instant createdAt;
    private String temperatureUnit;
    private String dateFormat;
    private Boolean compactMode;
    private Boolean liveChartAnimations;
    private Boolean dashboardAutoRefresh;

    public UserResponse(
            String id,
            String fullName,
            String email,
            String role
    ) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public static UserResponse from(User user) {
        UserResponse response = new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
        response.hasProfileImage = user.getProfileImageId() != null
                && !user.getProfileImageId().isBlank();
        response.firstName = user.getFirstName();
        response.lastName = user.getLastName();
        response.phoneNumber = user.getPhoneNumber();
        response.jobTitle = user.getJobTitle();
        response.department = user.getDepartment();
        response.bio = user.getBio();
        response.timeZone = user.getTimeZone();
        response.createdAt = user.getCreatedAt();
        response.temperatureUnit = user.getTemperatureUnit() == null
                ? "CELSIUS" : user.getTemperatureUnit();
        response.dateFormat = user.getDateFormat() == null
                ? "DD/MM/YYYY" : user.getDateFormat();
        response.compactMode = user.getCompactMode() != null && user.getCompactMode();
        response.liveChartAnimations = user.getLiveChartAnimations() == null
                || user.getLiveChartAnimations();
        response.dashboardAutoRefresh = user.getDashboardAutoRefresh() == null
                || user.getDashboardAutoRefresh();

        if ((response.firstName == null || response.firstName.isBlank())
                && (response.lastName == null || response.lastName.isBlank())
                && response.fullName != null) {
            String[] nameParts = response.fullName.trim().split("\\s+", 2);
            response.firstName = nameParts[0];
            response.lastName = nameParts.length > 1 ? nameParts[1] : "";
        }

        return response;
    }

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public boolean isHasProfileImage() {
        return hasProfileImage;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getJobTitle() { return jobTitle; }
    public String getDepartment() { return department; }
    public String getBio() { return bio; }
    public String getTimeZone() { return timeZone; }
    public Instant getCreatedAt() { return createdAt; }
    public String getTemperatureUnit() { return temperatureUnit; }
    public String getDateFormat() { return dateFormat; }
    public Boolean getCompactMode() { return compactMode; }
    public Boolean getLiveChartAnimations() { return liveChartAnimations; }
    public Boolean getDashboardAutoRefresh() { return dashboardAutoRefresh; }
}
