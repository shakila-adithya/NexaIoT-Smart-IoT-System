package com.nexaiot.server.report;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
public interface GeneratedReportRepository extends MongoRepository<GeneratedReport, String> {
    List<GeneratedReport> findByOwnerEmailOrderByGeneratedAtDesc(String ownerEmail);
    Optional<GeneratedReport> findByIdAndOwnerEmail(String id, String ownerEmail);
}
