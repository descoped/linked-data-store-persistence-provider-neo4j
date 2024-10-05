import io.descoped.lds.api.persistence.PersistenceInitializer;
import io.descoped.lds.core.persistence.neo4j.Neo4jInitializer;

module io.descoped.lds.persistence.neo4j {
    requires io.descoped.lds.persistence.api;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires org.neo4j.driver;
    requires java.logging;
    requires jul_to_slf4j;
    requires io.reactivex.rxjava2;
    requires org.reactivestreams;

    provides PersistenceInitializer with Neo4jInitializer;
}
