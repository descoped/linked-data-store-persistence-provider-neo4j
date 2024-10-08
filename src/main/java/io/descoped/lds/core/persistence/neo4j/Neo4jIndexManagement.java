package io.descoped.lds.core.persistence.neo4j;

import org.neo4j.driver.Result;
import org.neo4j.driver.Value;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class Neo4jIndexManagement {

    private final Set<Index> wantedIndexes;
    private final Set<Index> currentIndexes;

    static class Index {
        final String label;
        final Set<String> properties;
        final boolean uniqueConstraint;

        Index(String label, List<String> properties, boolean uniqueConstraint) {
            this.label = label;
            this.properties = new LinkedHashSet<>(properties);
            this.uniqueConstraint = uniqueConstraint;
        }

        String createUniqueConstraintCypher() {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE CONSTRAINT ON (n:");
            sb.append(label);
            sb.append(") ASSERT ");
            sb.append(properties.stream().map(k -> "n." + k).collect(Collectors.joining(", ")));
            sb.append(" IS UNIQUE;");
            return sb.toString();
        }

        String createIndexCypher() {
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE INDEX ON :");
            sb.append(label);
            sb.append("(");
            sb.append(properties.stream().collect(Collectors.joining(", ")));
            sb.append(");");
            return sb.toString();
        }

        String createCypherStatement() {
            if (uniqueConstraint) {
                return createUniqueConstraintCypher();
            } else {
                return createIndexCypher();
            }
        }

        String deleteCypherStatement() {
            StringBuilder sb = new StringBuilder();
            sb.append("DROP INDEX ON :");
            sb.append(label);
            sb.append("(");
            sb.append(properties.stream().collect(Collectors.joining(", ")));
            sb.append(");");
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Index index = (Index) o;
            return label.equals(index.label) &&
                    properties.equals(index.properties);
        }

        @Override
        public int hashCode() {
            return Objects.hash(label, properties);
        }

        @Override
        public String toString() {
            return "Index{" +
                    "label='" + label + '\'' +
                    ", properties=" + properties +
                    '}';
        }
    }

    Neo4jIndexManagement(Neo4jTransaction transaction, String namespace, Set<String> managedDomains) {
        currentIndexes = new LinkedHashSet<>();
        Result statementResult = transaction.executeCypher("CALL db.indexes");
        statementResult.forEachRemaining(record -> {
            Value labelValue = record.get("labelsOrTypes");
            List<String> labels = labelValue.asList(Value::asString);
            if (labels.size() > 0) {
                String label = labels.get(0);
                Value properties = record.get("properties");
                List<String> propertyList = properties.asList(Value::asString);
                currentIndexes.add(new Index(label, propertyList, !label.endsWith("_E")));
            }
        });
        wantedIndexes = new LinkedHashSet<>();
        for (String managedDomain : managedDomains) {
            wantedIndexes.add(new Index(managedDomain, List.of("id"), true));
            wantedIndexes.add(new Index(managedDomain + "_E", List.of("path", "hashOrValue"), false));
        }
    }

    void createMissingIndices(Neo4jTransaction transaction) {
        for (Index index : currentIndexes) {
            if (!wantedIndexes.contains(index)) {
                System.out.format("Not using index: %s. Use the following cypher statement to drop: %s%n", index.toString(), index.deleteCypherStatement());
            }
        }
        for (Index index : wantedIndexes) {
            if (!currentIndexes.contains(index)) {
                String cypherStatement = index.createCypherStatement();
                transaction.executeCypher(cypherStatement);
            }
        }
    }
}
