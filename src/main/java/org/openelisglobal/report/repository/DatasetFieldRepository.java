package org.openelisglobal.report.repository;

import org.openelisglobal.report.metadata.Dataset;
import org.openelisglobal.report.metadata.DatasetField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasetFieldRepository extends JpaRepository<DatasetField, Long> {
    List<DatasetField> findByDataset(Dataset dataset);
}
