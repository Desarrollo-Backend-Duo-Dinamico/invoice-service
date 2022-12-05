package com.invoice.api.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.invoice.api.entity.Invoice;

@Repository
public interface RepoInvoice extends JpaRepository<Invoice, Integer>{

	List<Invoice> findByRfcAndStatus(String rfc, Integer status);

	@Query(value ="SELECT * FROM invoice  WHERE rfc = :rfc AND created_at = :date  AND total = :total AND status = 1", nativeQuery = true)
	Invoice findByRfcAndCreate_atAndTotal(@Param("rfc") String rfc, @Param("date") LocalDateTime date, @Param("total") double total );
}
