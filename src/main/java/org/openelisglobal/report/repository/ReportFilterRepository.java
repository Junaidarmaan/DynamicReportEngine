package org.openelisglobal.report.repository;

import org.openelisglobal.report.metadata.Report;
import org.openelisglobal.report.metadata.ReportFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportFilterRepository extends JpaRepository<ReportFilter, Long> {
    List<ReportFilter> findByReport(Report report);
}
