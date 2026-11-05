package com.nexaiot.server.service;

import com.nexaiot.server.device.Device;
import com.nexaiot.server.exception.InvalidCredentialsException;
import com.nexaiot.server.exception.UserNotFoundException;
import com.nexaiot.server.model.User;
import com.nexaiot.server.repository.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountDeletionService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MongoTemplate mongo;
    private final GridFsTemplate gridFs;

    public AccountDeletionService(UserRepository users, PasswordEncoder encoder, MongoTemplate mongo, ObjectProvider<GridFsTemplate> gridFs) {
        this.users = users; this.encoder = encoder; this.mongo = mongo; this.gridFs = gridFs.getIfAvailable();
    }

    public void deleteSelf(String email, String password) {
        User user = findUser(email);
        if (!encoder.matches(password, user.getPasswordHash())) throw new InvalidCredentialsException("Current password is incorrect");
        deleteUser(user);
    }

    public void deleteUser(String id, String requester) {
        User user = users.findById(id).orElseThrow(() -> new UserNotFoundException("User not found"));
        if (user.getEmail().equalsIgnoreCase(requester)) throw new IllegalArgumentException("An administrator cannot delete the current account.");
        deleteUser(user);
    }

    public void deleteDevice(String id) {
        Device device = mongo.findById(id, Device.class);
        if (device == null) throw new IllegalArgumentException("Device not found");
        mongo.remove(Query.query(Criteria.where("deviceId").is(id)), "sensor_readings");
        mongo.remove(Query.query(Criteria.where("deviceId").is(id)), "alerts");
        mongo.remove(Query.query(Criteria.where("deviceId").is(id)), "alertRules");
        mongo.remove(Query.query(Criteria.where("deviceId").is(id)), "maintenance_tasks");
        mongo.remove(Query.query(Criteria.where("deviceId").is(id)), "generated_reports");
        mongo.remove(Query.query(Criteria.where("deviceId").is(id)), "provisioning_sessions");
        mongo.remove(Query.query(Criteria.where("deviceId").is(id)), "device_enrollment_operations");
        mongo.remove(Query.query(Criteria.where("_id").is(id)), "devices");
    }

    private void deleteUser(User user) {
        String email = user.getEmail().trim().toLowerCase();
        List<Device> devices = mongo.find(Query.query(Criteria.where("ownerEmail").is(email)), Device.class);
        devices.forEach(device -> deleteDevice(device.getId()));
        for (String collection : List.of("alerts", "alertRules", "maintenance_tasks", "generated_reports", "provisioning_sessions", "device_enrollment_operations", "password_reset_tokens")) {
            mongo.remove(Query.query(Criteria.where("ownerEmail").is(email)), collection);
        }
        if (gridFs != null) gridFs.delete(Query.query(Criteria.where("metadata.ownerEmail").is(email)));
        users.delete(user);
    }

    private User findUser(String email) { return users.findByEmail(email.trim().toLowerCase()).orElseThrow(() -> new UserNotFoundException("User not found")); }
}
