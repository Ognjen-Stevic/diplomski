package com.ognjen.diplomski.entity.enums;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequisitionStatusSchemaTests {

    private static final Pattern SCHEMA_STATUS_CONSTRAINT = Pattern.compile(
            "CONSTRAINT chk_requisition_status CHECK \\(.*?ARRAY\\[(.*?)]",
            Pattern.DOTALL
    );
    private static final Pattern MIGRATION_STATUS_CONSTRAINT = Pattern.compile(
            "ADD CONSTRAINT chk_requisition_status CHECK \\(status IN \\((.*?)\\)\\)",
            Pattern.DOTALL
    );
    private static final Pattern QUOTED_VALUE = Pattern.compile("'([A-Z_]+)'");

    @Test
    void schemaSnapshotAcceptsExactlyTheJavaRequisitionStatuses() throws IOException {
        assertEquals(javaStatuses(), constraintStatuses(
                Path.of("database", "schema.sql"),
                SCHEMA_STATUS_CONSTRAINT
        ));
    }

    @Test
    void workflowMigrationAcceptsExactlyTheJavaRequisitionStatuses() throws IOException {
        assertEquals(javaStatuses(), constraintStatuses(
                Path.of("src", "main", "resources", "db", "requisition_status_workflow_migration.sql"),
                MIGRATION_STATUS_CONSTRAINT
        ));
    }

    private static Set<String> javaStatuses() {
        return Arrays.stream(RequisitionStatus.values())
                .map(Enum::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> constraintStatuses(Path path, Pattern constraintPattern)
            throws IOException {
        Matcher constraint = constraintPattern.matcher(Files.readString(path));
        if (!constraint.find()) {
            throw new AssertionError("Requisition status constraint not found in " + path);
        }

        return QUOTED_VALUE.matcher(constraint.group(1)).results()
                .map(result -> result.group(1))
                .collect(Collectors.toUnmodifiableSet());
    }
}
