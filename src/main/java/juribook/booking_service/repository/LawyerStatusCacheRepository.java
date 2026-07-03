package juribook.booking_service.repository;

import juribook.booking_service.entity.LawyerStatusCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LawyerStatusCacheRepository extends JpaRepository<LawyerStatusCache, Long> {
}