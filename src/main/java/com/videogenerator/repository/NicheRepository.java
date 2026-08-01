package com.videogenerator.repository;

import com.videogenerator.model.NicheData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Repository for managing niche data persistence in SQLite database.
 */
public class NicheRepository {
    private static final Logger logger = LoggerFactory.getLogger(NicheRepository.class);
    private final DatabaseManager dbManager;

    public NicheRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Saves a niche to the database.
     *
     * @param niche the niche data to save
     * @return the saved niche with generated ID
     * @throws SQLException if save fails
     */
    public NicheData save(NicheData niche) throws SQLException {
        String sql = "INSERT INTO niches (topic, keywords, competition_score, viral_potential, " +
                     "monetization_score, overall_score, last_updated) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, niche.getTopic());
            stmt.setString(2, niche.getKeywordsAsString());
            stmt.setDouble(3, niche.getCompetitionScore());
            stmt.setDouble(4, niche.getViralPotential());
            stmt.setDouble(5, niche.getMonetizationScore());
            stmt.setDouble(6, niche.getOverallScore());
            stmt.setString(7, Timestamp.valueOf(niche.getLastUpdated() != null ?
                            niche.getLastUpdated() : LocalDateTime.now()).toString());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Failed to insert niche, no rows affected");
            }

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    niche.setId(rs.getInt(1));
                }
            }

            logger.info("Saved niche: {} with ID: {}", niche.getTopic(), niche.getId());
            return niche;
        }
    }

    /**
     * Finds all niches ordered by overall score (descending).
     *
     * @return list of niches
     */
    public List<NicheData> findAll() {
        return findAll("overall_score DESC");
    }

    /**
     * Finds all niches with custom ordering.
     *
     * @param orderBy SQL order by clause
     * @return list of niches
     */
    public List<NicheData> findAll(String orderBy) {
        List<NicheData> niches = new ArrayList<>();
        String sql = "SELECT * FROM niches ORDER BY " + orderBy;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                niches.add(mapResultSetToNiche(rs));
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve niches", e);
        }

        return niches;
    }

    /**
     * Finds top N niches by overall score.
     *
     * @param limit maximum number of niches to return
     * @return list of top niches
     */
    public List<NicheData> findTopNiches(int limit) {
        List<NicheData> niches = new ArrayList<>();
        String sql = "SELECT * FROM niches ORDER BY overall_score DESC LIMIT ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    niches.add(mapResultSetToNiche(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve top niches", e);
        }

        return niches;
    }

    /**
     * Finds niches above a minimum score threshold.
     *
     * @param minScore minimum overall score
     * @return list of qualifying niches
     */
    public List<NicheData> findByMinScore(double minScore) {
        List<NicheData> niches = new ArrayList<>();
        String sql = "SELECT * FROM niches WHERE overall_score >= ? ORDER BY overall_score DESC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDouble(1, minScore);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    niches.add(mapResultSetToNiche(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve niches by score", e);
        }

        return niches;
    }

    /**
     * Finds a niche by topic.
     *
     * @param topic the niche topic
     * @return the niche data or null if not found
     */
    public NicheData findByTopic(String topic) {
        String sql = "SELECT * FROM niches WHERE topic = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, topic);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToNiche(rs);
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve niche by topic: {}", topic, e);
        }

        return null;
    }

    /**
     * Finds niches updated after a specific date.
     *
     * @param since the date threshold
     * @return list of recently updated niches
     */
    public List<NicheData> findUpdatedAfter(LocalDateTime since) {
        List<NicheData> niches = new ArrayList<>();
        String sql = "SELECT * FROM niches WHERE last_updated >= ? ORDER BY last_updated DESC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, Timestamp.valueOf(since).toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    niches.add(mapResultSetToNiche(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve recently updated niches", e);
        }

        return niches;
    }

    /**
     * Updates an existing niche.
     *
     * @param niche the niche to update
     * @return true if update successful
     */
    public boolean update(NicheData niche) {
        String sql = "UPDATE niches SET keywords = ?, competition_score = ?, viral_potential = ?, " +
                     "monetization_score = ?, overall_score = ?, last_updated = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, niche.getKeywordsAsString());
            stmt.setDouble(2, niche.getCompetitionScore());
            stmt.setDouble(3, niche.getViralPotential());
            stmt.setDouble(4, niche.getMonetizationScore());
            stmt.setDouble(5, niche.getOverallScore());
            stmt.setString(6, Timestamp.valueOf(LocalDateTime.now()).toString());
            stmt.setInt(7, niche.getId());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                logger.info("Updated niche: {}", niche.getTopic());
                return true;
            }

        } catch (SQLException e) {
            logger.error("Failed to update niche: {}", niche.getTopic(), e);
        }

        return false;
    }

    /**
     * Deletes a niche by ID.
     *
     * @param id the niche ID
     * @return true if deletion successful
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM niches WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                logger.info("Deleted niche with ID: {}", id);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Failed to delete niche with ID: {}", id, e);
        }

        return false;
    }

    /**
     * Deletes old niches (older than specified days).
     *
     * @param daysOld number of days threshold
     * @return number of deleted rows
     */
    public int deleteOlderThan(int daysOld) {
        String sql = "DELETE FROM niches WHERE last_updated < ?";
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysOld);

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, Timestamp.valueOf(threshold).toString());
            int affectedRows = stmt.executeUpdate();

            logger.info("Deleted {} old niches (older than {} days)", affectedRows, daysOld);
            return affectedRows;

        } catch (SQLException e) {
            logger.error("Failed to delete old niches", e);
        }

        return 0;
    }

    /**
     * Maps a SQL ResultSet row to a NicheData object.
     *
     * @param rs the result set
     * @return mapped niche data
     * @throws SQLException if mapping fails
     */
    private NicheData mapResultSetToNiche(ResultSet rs) throws SQLException {
        NicheData niche = new NicheData();
        niche.setId(rs.getInt("id"));
        niche.setTopic(rs.getString("topic"));

        String keywordsStr = rs.getString("keywords");
        if (keywordsStr != null && !keywordsStr.isEmpty()) {
            niche.setKeywords(Arrays.asList(keywordsStr.split(",\\s*")));
        }

        niche.setCompetitionScore(rs.getDouble("competition_score"));
        niche.setViralPotential(rs.getDouble("viral_potential"));
        niche.setMonetizationScore(rs.getDouble("monetization_score"));
        niche.setOverallScore(rs.getDouble("overall_score"));

        Timestamp timestamp = Timestamp.valueOf(rs.getString("last_updated"));
        niche.setLastUpdated(timestamp.toLocalDateTime());

        return niche;
    }
}
