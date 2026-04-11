package org.openelisglobal.report.repository;

import org.openelisglobal.report.metadata.Report;
import org.openelisglobal.report.metadata.ReportColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportColumnRepository extends JpaRepository<ReportColumn, Long> {
    List<ReportColumn> findByReportOrderByOrderIndex(Report report);
}
