import java.sql.*;
public class SQLink {


    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:C:\\Users\\Chan Myae May\\IdeaProjects\\ATMWithChatbot2.0\\bankatm.db")) {
            System.out.println("SQLite connected!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
