import java.sql.*;

public class DBHelper {
    private static final String DB_URL = "jdbc:sqlite:C:\\Users\\Chan Myae May\\IdeaProjects\\ATMWithChatbot2.0\\bankatm.db";


    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public static void initializeDatabase() {
        String createUsers = "CREATE TABLE IF NOT EXISTS users (" +
                "username TEXT PRIMARY KEY," +
                "salt BLOB NOT NULL," +
                "password_hash BLOB NOT NULL," +
                "balance REAL NOT NULL" +
                ");";

        String createTransactions = "CREATE TABLE IF NOT EXISTS transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "username TEXT NOT NULL," +
                "timestamp TEXT NOT NULL," +
                "action TEXT NOT NULL," +
                "FOREIGN KEY(username) REFERENCES users(username)" +
                ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createUsers);
            stmt.execute(createTransactions);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
