package com.videogenerator.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages SQLite database connections and schema initialization.
 * Implements singleton pattern for connection pool management.
 */
public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;
    private static final String DB_FILE = "data/videogenerator.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;

    private DatabaseManager() {
        initializeDatabase();
    }

    /**
     * Gets the singleton instance of DatabaseManager.
     *
     * @return DatabaseManager instance
     */
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    /**
     * Gets a new database connection.
     *
     * @return SQL Connection
     * @throws SQLException if connection fails
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Initializes database file and creates schema if not exists.
     */
    private void initializeDatabase() {
        try {
            // Create data directory if not exists
            File dbFile = new File(DB_FILE);
            File dataDir = dbFile.getParentFile();
            if (!dataDir.exists()) {
                if (dataDir.mkdirs()) {
                    logger.info("Created data directory: {}", dataDir.getAbsolutePath());
                }
            }

            // Create tables
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                // Niches table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS niches (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    topic TEXT NOT NULL," +
                    "    keywords TEXT," +
                    "    competition_score REAL," +
                    "    viral_potential REAL," +
                    "    monetization_score REAL," +
                    "    overall_score REAL," +
                    "    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")"
                );

                // Content ideas table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS content_ideas (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    niche_id INTEGER," +
                    "    title TEXT NOT NULL," +
                    "    hook TEXT," +
                    "    description TEXT," +
                    "    hashtags TEXT," +
                    "    estimated_ctr REAL," +
                    "    created_date DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    FOREIGN KEY (niche_id) REFERENCES niches(id)" +
                    ")"
                );

                // Trends table
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS trends (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    topic TEXT NOT NULL," +
                    "    keyword TEXT," +
                    "    search_volume INTEGER," +
                    "    trend_score REAL," +
                    "    source TEXT," +
                    "    date DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ")"
                );

                // Video history table (for tracking uploaded videos)
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS video_history (" +
                    "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "    video_id TEXT UNIQUE," +
                    "    title TEXT," +
                    "    niche TEXT," +
                    "    content_idea_id INTEGER," +
                    "    upload_date DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "    views INTEGER DEFAULT 0," +
                    "    likes INTEGER DEFAULT 0," +
                    "    comments INTEGER DEFAULT 0," +
                    "    last_synced DATETIME," +
                    "    FOREIGN KEY (content_idea_id) REFERENCES content_ideas(id)" +
                    ")"
                );

                // Create indexes for better query performance
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_niches_score ON niches(overall_score DESC)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_niches_updated ON niches(last_updated DESC)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_ideas_niche ON content_ideas(niche_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_ideas_ctr ON content_ideas(estimated_ctr DESC)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_trends_date ON trends(date DESC)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_video_history_upload ON video_history(upload_date DESC)");

                logger.info("Database initialized successfully at: {}", dbFile.getAbsolutePath());
            }

        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    /**
     * Tests database connection.
     *
     * @return true if connection successful
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            logger.error("Database connection test failed", e);
            return false;
        }
    }

    /**
     * Closes all database connections and cleans up resources.
     */
    public void shutdown() {
        logger.info("Database manager shutting down");
        // SQLite doesn't require explicit connection pool cleanup
        // Connections are closed when try-with-resources completes
    }
}
