package com.nexaiot.server.task;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface MaintenanceTaskRepository extends MongoRepository<MaintenanceTask, String> {
    List<MaintenanceTask> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);
    Optional<MaintenanceTask> findByIdAndOwnerEmail(String id, String ownerEmail);
}
