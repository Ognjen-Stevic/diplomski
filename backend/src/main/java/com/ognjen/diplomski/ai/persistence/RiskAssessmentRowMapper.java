package com.ognjen.diplomski.ai.persistence;

import static com.ognjen.diplomski.ai.domain.RiskTypes.*;

import com.ognjen.diplomski.ai.domain.CustomerFeatures;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/** Maps the joined analytics projection without mixing mapping into query construction. */
final class RiskAssessmentRowMapper {
  private final ObjectMapper mapper;

  RiskAssessmentRowMapper(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  Assessment detail(ResultSet result) throws SQLException {
    String level = result.getString("level"), data = result.getString("features");
    Instant at = at(result);
    Level risk = level == null ? Level.INSUFFICIENT_DATA : Level.valueOf(level);
    return new Assessment(
        risk,
        at,
        result.getString("status"),
        data == null ? null : mapper.readValue(data, Features.class),
        result.getString("explanation") == null
            ? List.of()
            : List.of(mapper.readValue(result.getString("explanation"), String[].class)),
        CustomerFeatures.recommendation(risk),
        stale(result, at));
  }

  RiskListItem list(ResultSet result) throws SQLException {
    String level = result.getString("level");
    Instant at = at(result);
    Level risk = level == null ? Level.INSUFFICIENT_DATA : Level.valueOf(level);
    return new RiskListItem(
        result.getLong("id"),
        result.getString("object_name"),
        risk,
        at,
        CustomerFeatures.recommendation(risk),
        stale(result, at));
  }

  RiskBatchItem batch(ResultSet result) throws SQLException {
    String level = result.getString("level");
    return new RiskBatchItem(
        result.getLong("id"),
        level == null ? Level.INSUFFICIENT_DATA : Level.valueOf(level));
  }

  private static Instant at(ResultSet result) throws SQLException {
    return result.getTimestamp("at") == null ? null : result.getTimestamp("at").toInstant();
  }

  private static boolean stale(ResultSet result, Instant at) throws SQLException {
    return at == null
        || at.isBefore(Instant.now().minusSeconds(86400 * 2))
        || !Objects.equals(result.getObject("active_version"), result.getObject("model_version"));
  }
}
