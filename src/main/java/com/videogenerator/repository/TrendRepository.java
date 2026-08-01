package com.videogenerator.repository;

import com.videogenerator.model.TrendData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for managing trend data persistence in SQLite database.
 */
public class TrendRepository {
    private static final Logger logger = LoggerFactory.getLogger(TrendRepository.class);
    private final DatabaseManager dbManager;

    public TrendRepository() {
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Saves a trend to the database.
     *
     * @param trend the trend data to save
     * @return the saved trend with generated ID
     * @throws SQLException if save fails
     */
    public TrendData save(TrendData trend) throws SQLException {
        String sql = "INSERT INTO trends (topic, keyword, search_volume, trend_score, source, date) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, trend.getTopic());
            stmt.setString(2, trend.getKeyword());
            stmt.setInt(3, trend.getSearchVolume());
            stmt.setDouble(4, trend.getTrendScore());
            stmt.setString(5, trend.getSource());
            stmt.setString(6, Timestamp.valueOf(trend.getDate() != null ?
                            trend.getDate() : LocalDateTime.now()).toString());

            int affectedRows = stmt.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Failed to insert trend, no rows affected");
            }

            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    trend.setId(rs.getInt(1));
                }
            }

            logger.debug("Saved trend: {} from source: {}", trend.getKeyword(), trend.getSource());
            return trend;
        }
    }

    /**
     * Saves multiple trends in a batch operation.
     *
     * @param trends list of trends to save
     * @return number of successfully saved trends
     */
    public int saveBatch(List<TrendData> trends) {
        String sql = "INSERT INTO trends (topic, keyword, search_volume, trend_score, source, date) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        int savedCount = 0;
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (TrendData trend : trends) {
                stmt.setString(1, trend.getTopic());
                stmt.setString(2, trend.getKeyword());
                stmt.setInt(3, trend.getSearchVolume());
                stmt.setDouble(4, trend.getTrendScore());
                stmt.setString(5, trend.getSource());
                stmt.setString(6, Timestamp.valueOf(trend.getDate() != null ?
                                trend.getDate() : LocalDateTime.now()).toString());
                stmt.addBatch();
            }

            int[] results = stmt.executeBatch();
            conn.commit();

            for (int result : results) {
                if (result > 0) savedCount++;
            }

            logger.info("Saved {} trends in batch operation", savedCount);

        } catch (SQLException e) {
            logger.error("Failed to save trends batch", e);
        }

        return savedCount;
    }

    /**
     * Finds all trends ordered by date (descending).
     *
     * @return list of trends
     */
    public List<TrendData> findAll() {
        return findAll("date DESC");
    }

    /**
     * Finds all trends with custom ordering.
     *
     * @param orderBy SQL order by clause
     * @return list of trends
     */
    public List<TrendData> findAll(String orderBy) {
        List<TrendData> trends = new ArrayList<>();
        String sql = "SELECT * FROM trends ORDER BY " + orderBy;

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                trends.add(mapResultSetToTrend(rs));
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve trends", e);
        }

        return trends;
    }

    /**
     * Finds recent trends (within last 24 hours).
     *
     * @return list of recent trends
     */
    public List<TrendData> findRecent() {
        return findAfterDate(LocalDateTime.now().minusDays(1));
    }

    /**
     * Finds trends after a specific date.
     *
     * @param since the date threshold
     * @return list of trends
     */
    public List<TrendData> findAfterDate(LocalDateTime since) {
        List<TrendData> trends = new ArrayList<>();
        String sql = "SELECT * FROM trends WHERE date >= ? ORDER BY date DESC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, Timestamp.valueOf(since).toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trends.add(mapResultSetToTrend(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve trends after date", e);
        }

        return trends;
    }

    /**
     * Finds trends by source.
     *
     * @param source the source name (e.g., "youtube", "google_trends")
     * @return list of trends from that source
     */
    public List<TrendData> findBySource(String source) {
        List<TrendData> trends = new ArrayList<>();
        String sql = "SELECT * FROM trends WHERE source = ? ORDER BY date DESC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, source);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trends.add(mapResultSetToTrend(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve trends by source: {}", source, e);
        }

        return trends;
    }

    /**
     * Finds trends by topic.
     *
     * @param topic the topic name
     * @return list of trends for that topic
     */
    public List<TrendData> findByTopic(String topic) {
        List<TrendData> trends = new ArrayList<>();
        String sql = "SELECT * FROM trends WHERE topic = ? ORDER BY date DESC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, topic);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trends.add(mapResultSetToTrend(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve trends by topic: {}", topic, e);
        }

        return trends;
    }

    /**
     * Finds top trending topics with high search volume.
     *
     * @param minVolume minimum search volume
     * @param limit maximum number of trends to return
     * @return list of top trends
     */
    public List<TrendData> findTopTrends(int minVolume, int limit) {
        List<TrendData> trends = new ArrayList<>();
        String sql = "SELECT * FROM trends WHERE search_volume >= ? " +
                     "ORDER BY trend_score DESC, search_volume DESC LIMIT ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, minVolume);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trends.add(mapResultSetToTrend(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve top trends", e);
        }

        return trends;
    }

    /**
     * Finds trends by keyword (partial match).
     *
     * @param keyword the keyword to search for
     * @return list of matching trends
     */
    public List<TrendData> findByKeyword(String keyword) {
        List<TrendData> trends = new ArrayList<>();
        String sql = "SELECT * FROM trends WHERE keyword LIKE ? ORDER BY date DESC";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, "%" + keyword + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    trends.add(mapResultSetToTrend(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Failed to retrieve trends by keyword: {}", keyword, e);
        }

        return trends;
    }

    /**
     * Deletes trends older than specified days.
     *
     * @param daysOld number of days threshold
     * @return number of deleted rows
     */
    public int deleteOlderThan(int daysOld) {
        String sql = "DELETE FROM trends WHERE date < ?";
        LocalDateTime threshold = LocalDateTime.now().minusDays(daysOld);

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, Timestamp.valueOf(threshold).toString());
            int affectedRows = stmt.executeUpdate();

            logger.info("Deleted {} old trends (older than {} days)", affectedRows, daysOld);
            return affectedRows;

        } catch (SQLException e) {
            logger.error("Failed to delete old trends", e);
        }

        return 0;
    }

    /**
     * Deletes all trends from a specific source.
     *
     * @param source the source name
     * @return number of deleted rows
     */
    public int deleteBySource(String source) {
        String sql = "DELETE FROM trends WHERE source = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, source);
            int affectedRows = stmt.executeUpdate();

            logger.info("Deleted {} trends from source: {}", affectedRows, source);
            return affectedRows;

        } catch (SQLException e) {
            logger.error("Failed to delete trends by source: {}", source, e);
        }

        return 0;
    }

    /**
     * Gets aggregate statistics for trends.
     *
     * @return map with count, avg_score, max_volume
     */
    public TrendStatistics getStatistics() {
        String sql = "SELECT COUNT(*) as count, AVG(trend_score) as avg_score, " +
                     "MAX(search_volume) as max_volume FROM trends";

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return new TrendStatistics(
                    rs.getInt("count"),
                    rs.getDouble("avg_score"),
                    rs.getInt("max_volume")
                );
            }

        } catch (SQLException e) {
            logger.error("Failed to get trend statistics", e);
        }

        return new TrendStatistics(0, 0.0, 0);
    }

    /**
     * Maps a SQL ResultSet row to a TrendData object.
     *
     * @param rs the result set
     * @return mapped trend data
     * @throws SQLException if mapping fails
     */
    private TrendData mapResultSetToTrend(ResultSet rs) throws SQLException {
        TrendData trend = new TrendData();
        trend.setId(rs.getInt("id"));
        trend.setTopic(rs.getString("topic"));
        trend.setKeyword(rs.getString("keyword"));
        trend.setSearchVolume(rs.getInt("search_volume"));
        trend.setTrendScore(rs.getDouble("trend_score"));
        trend.setSource(rs.getString("source"));

        Timestamp timestamp = Timestamp.valueOf(rs.getString("date"));
        trend.setDate(timestamp.toLocalDateTime());

        return trend;
    }

    /**
     * Simple statistics holder for trend data.
     */
    public static class TrendStatistics {
        public final int count;
        public final double avgScore;
        public final int maxVolume;

        public TrendStatistics(int count, double avgScore, int maxVolume) {
            this.count = count;
            this.avgScore = avgScore;
            this.maxVolume = maxVolume;
        }

        @Override
        public String toString() {
            return String.format("TrendStatistics{count=%d, avgScore=%.2f, maxVolume=%d}",
                    count, avgScore, maxVolume);
        }
    }
}
